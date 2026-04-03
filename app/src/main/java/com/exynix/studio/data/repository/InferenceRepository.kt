package com.exynix.studio.data.repository

import android.content.Context
import android.util.Log
import com.exynix.studio.data.models.*
import com.exynix.studio.inference.jni.InferenceJni
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExynNix-Repo"

@Singleton
class InferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _models = MutableStateFlow<List<ModelEntry>>(ModelCatalog.recommended)
    val models: StateFlow<List<ModelEntry>> = _models.asStateFlow()

    private val _activeStats = MutableStateFlow<InferenceStats?>(null)
    val activeStats: StateFlow<InferenceStats?> = _activeStats.asStateFlow()

    private val _hardwareProfile = MutableStateFlow<HardwareProfile?>(null)
    val hardwareProfile: StateFlow<HardwareProfile?> = _hardwareProfile.asStateFlow()

    private val _cpuTemp = MutableStateFlow(0.0f)
    val cpuTemp: StateFlow<Float> = _cpuTemp.asStateFlow()

    private var nativeHandle: Long = -1L
    private var currentModelId: String? = null

    // ── Hardware ───────────────────────────────────────────────────────────────

    suspend fun loadHardwareProfile(): HardwareProfile = withContext(Dispatchers.IO) {
        val json = InferenceJni.nativeGetHardwareProfile()
        Log.d(TAG, "Hardware profile: $json")
        val parsed = parseHardwareProfile(json)
        _hardwareProfile.value = parsed
        parsed
    }

    fun startThermalPolling(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                _cpuTemp.value = InferenceJni.nativeGetCpuTemperature()
                delay(3000L)
            }
        }
    }

    // ── Model lifecycle ────────────────────────────────────────────────────────

    suspend fun loadModel(config: InferenceConfig, onProgress: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val model = _models.value.firstOrNull { it.id == config.modelId }
                ?: return@withContext false

            if (model.localPath.isEmpty()) {
                Log.e(TAG, "Model not downloaded: ${model.id}")
                return@withContext false
            }

            onProgress("Creating session...")
            val backendStr = when (config.backend) {
                InferenceBackend.AUTO -> InferenceJni.nativeAutoSelectBackend(model.localPath)
                else -> config.backend.name
            }

            if (nativeHandle != -1L) {
                InferenceJni.nativeDestroySession(nativeHandle)
            }

            nativeHandle = InferenceJni.nativeCreateSession(backendStr)
            onProgress("Loading model weights...")

            val nGpuLayers = if (config.nGpuLayers == -1) {
                when (backendStr) {
                    "GPU_VULKAN" -> 24  // Optimal for Exynos 990 + 12GB RAM on 7B Q4
                    else -> 0
                }
            } else config.nGpuLayers

            val success = InferenceJni.nativeLoadModel(
                handle = nativeHandle,
                path = model.localPath,
                nCtx = config.nCtx,
                nThreads = config.nThreads,
                nGpuLayers = nGpuLayers,
                temperature = config.temperature,
                topP = config.topP,
                maxNewTokens = config.maxNewTokens
            )

            if (success) {
                currentModelId = config.modelId
                updateModelStatus(config.modelId, ModelStatus.READY)
                onProgress("Model ready on $backendStr")
            } else {
                nativeHandle = -1L
                onProgress("Failed to load model")
            }

            success
        }

    fun unloadModel() {
        if (nativeHandle != -1L) {
            InferenceJni.nativeUnloadModel(nativeHandle)
            InferenceJni.nativeDestroySession(nativeHandle)
            nativeHandle = -1L
            currentModelId = null
        }
    }

    // ── Inference ──────────────────────────────────────────────────────────────

    fun generate(
        config: InferenceConfig,
        prompt: String,
        onToken: (String) -> Unit,
        onDone: (InferenceStats) -> Unit,
        onError: (String) -> Unit
    ) {
        if (nativeHandle == -1L) {
            onError("No model loaded")
            return
        }

        val callback = object : InferenceJni.TokenCallback {
            override fun onToken(token: String, isDone: Boolean) {
                if (!isDone) onToken(token)
            }
            override fun onStats(
                promptTps: Float, genTps: Float,
                promptTokens: Int, genTokens: Int,
                ttftMs: Long, totalMs: Long,
                backend: String, peakTemp: Float, peakMemMb: Long
            ) {
                val stats = InferenceStats(promptTps, genTps, promptTokens, genTokens,
                    ttftMs, totalMs, backend, peakTemp, peakMemMb)
                _activeStats.value = stats
                onDone(stats)
            }
        }

        val statsJson = InferenceJni.nativeGenerate(nativeHandle, prompt, callback)
        // Parse stats if callback wasn't called
        try {
            val json = JSONObject(statsJson)
            val stats = InferenceStats(
                promptTps = json.optDouble("prompt_tps", 0.0).toFloat(),
                genTps = json.optDouble("gen_tps", 0.0).toFloat(),
                promptTokens = json.optInt("prompt_tokens"),
                genTokens = json.optInt("gen_tokens"),
                ttftMs = json.optLong("ttft_ms"),
                totalMs = json.optLong("total_ms"),
                backendUsed = json.optString("backend"),
                peakTemp = json.optDouble("peak_temp", 0.0).toFloat()
            )
            _activeStats.value = stats
            onDone(stats)
        } catch (e: Exception) {
            Log.w(TAG, "Stats parse failed: ${e.message}")
        }
    }

    fun cancelGeneration() {
        if (nativeHandle != -1L) {
            InferenceJni.nativeCancel(nativeHandle)
        }
    }

    // ── Model download ─────────────────────────────────────────────────────────

    fun downloadModel(
        modelId: String,
        scope: CoroutineScope,
        onProgress: (Float) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            val model = _models.value.firstOrNull { it.id == modelId }
                ?: run { onError("Model not found: $modelId"); return@launch }

            val dir = File(context.filesDir, "models")
            dir.mkdirs()

            val fileName = model.downloadUrl.substringAfterLast("/")
            val outFile = File(dir, fileName)

            if (outFile.exists() && outFile.length() > 0) {
                updateModelPath(modelId, outFile.absolutePath)
                onComplete(outFile.absolutePath)
                return@launch
            }

            updateModelStatus(modelId, ModelStatus.DOWNLOADING)

            try {
                val url = URL(model.downloadUrl)
                val connection = url.openConnection()
                connection.connect()
                val total = connection.contentLengthLong

                connection.getInputStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buf = ByteArray(32768)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                withContext(Dispatchers.Main) {
                                    onProgress(downloaded.toFloat() / total)
                                }
                                updateDownloadProgress(modelId, downloaded.toFloat() / total)
                            }
                        }
                    }
                }

                updateModelPath(modelId, outFile.absolutePath)
                updateModelStatus(modelId, ModelStatus.READY)
                withContext(Dispatchers.Main) { onComplete(outFile.absolutePath) }

            } catch (e: IOException) {
                outFile.delete()
                updateModelStatus(modelId, ModelStatus.ERROR)
                withContext(Dispatchers.Main) { onError(e.message ?: "Download failed") }
            }
        }
    }

    // ── Benchmark ──────────────────────────────────────────────────────────────

    suspend fun runBenchmark(nTokens: Int = 64): String? = withContext(Dispatchers.IO) {
        if (nativeHandle == -1L) return@withContext null
        InferenceJni.nativeBenchmark(nativeHandle, nTokens)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun updateModelStatus(id: String, status: ModelStatus) {
        _models.value = _models.value.map {
            if (it.id == id) it.copy(status = status.name) else it
        }
    }

    private fun updateModelPath(id: String, path: String) {
        _models.value = _models.value.map {
            if (it.id == id) it.copy(localPath = path, status = ModelStatus.READY.name) else it
        }
    }

    private fun updateDownloadProgress(id: String, progress: Float) {
        _models.value = _models.value.map {
            if (it.id == id) it.copy(downloadProgress = progress) else it
        }
    }

    private fun parseHardwareProfile(json: String): HardwareProfile {
        return try {
            val j = JSONObject(json)
            val cpu = j.optJSONObject("cpu")
            val gpu = j.optJSONObject("gpu")
            val nnapi = j.optJSONObject("nnapi")
            HardwareProfile(
                socName = j.optString("soc", "Exynos 990"),
                deviceModel = j.optString("device", "Galaxy S20 Ultra"),
                recommendedBackend = j.optString("recommended_backend", "GPU_VULKAN"),
                recommendedReason = j.optString("recommended_reason", ""),
                cpu = CpuInfo(
                    arch = cpu?.optString("arch", "ARMv8.2-A") ?: "ARMv8.2-A",
                    bigCores = cpu?.optInt("big_cores", 2) ?: 2,
                    midCores = cpu?.optInt("mid_cores", 2) ?: 2,
                    littleCores = cpu?.optInt("little_cores", 4) ?: 4,
                    hasDotprod = cpu?.optBoolean("has_dotprod", true) ?: true,
                    hasFp16 = cpu?.optBoolean("has_fp16", true) ?: true,
                    totalRamMb = cpu?.optLong("total_ram_mb", 12288L) ?: 12288L,
                    availRamMb = cpu?.optLong("avail_ram_mb", 4096L) ?: 4096L
                ),
                gpu = GpuInfo(
                    available = gpu?.optBoolean("available", false) ?: false,
                    deviceName = gpu?.optString("device_name", "Mali-G77 MP11") ?: "Mali-G77 MP11",
                    isMali = gpu?.optBoolean("is_mali", true) ?: true,
                    totalVramMb = gpu?.optLong("total_vram_mb", 0L) ?: 0L,
                    supportsFp16 = gpu?.optBoolean("supports_fp16", true) ?: true
                ),
                nnapi = NnapiInfo(
                    available = nnapi?.optBoolean("available", false) ?: false,
                    deviceCount = nnapi?.optInt("device_count", 0) ?: 0,
                    hasNpu = nnapi?.optBoolean("has_npu", false) ?: false,
                    hasGpu = nnapi?.optBoolean("has_gpu", false) ?: false,
                    hasCpu = nnapi?.optBoolean("has_cpu", true) ?: true
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Profile parse failed: ${e.message}")
            HardwareProfile()
        }
    }
}
