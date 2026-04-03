package com.exynix.studio.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exynix.studio.data.models.*
import com.exynix.studio.data.repository.InferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: InferenceRepository
) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    val models = repo.models
    val hardwareProfile = repo.hardwareProfile
    val cpuTemp = repo.cpuTemp
    val activeStats = repo.activeStats

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageUi>> = _chatMessages.asStateFlow()

    private val _streamBuffer = MutableStateFlow("")
    val streamBuffer: StateFlow<String> = _streamBuffer.asStateFlow()

    private val _benchmarkResults = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val benchmarkResults: StateFlow<List<BenchmarkResult>> = _benchmarkResults.asStateFlow()

    var inferenceConfig = InferenceConfig(
        modelId = ModelCatalog.recommended.first().id
    )
        private set

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            repo.loadHardwareProfile()
        }
        repo.startThermalPolling(viewModelScope)
    }

    // ── Model management ──────────────────────────────────────────────────────

    fun downloadModel(modelId: String) {
        repo.downloadModel(
            modelId = modelId,
            scope = viewModelScope,
            onProgress = { progress ->
                _uiState.update { it.copy(downloadProgress = modelId to progress) }
            },
            onComplete = { path ->
                _uiState.update { it.copy(
                    statusMessage = "Downloaded to $path",
                    downloadProgress = null
                ) }
            },
            onError = { err ->
                _uiState.update { it.copy(errorMessage = err, downloadProgress = null) }
            }
        )
    }

    fun loadModel(modelId: String, backend: InferenceBackend = InferenceBackend.AUTO) {
        inferenceConfig = inferenceConfig.copy(modelId = modelId, backend = backend)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Loading model...") }
            val ok = repo.loadModel(inferenceConfig) { msg ->
                _uiState.update { it.copy(statusMessage = msg) }
            }
            _uiState.update { it.copy(
                isLoading = false,
                isModelLoaded = ok,
                statusMessage = if (ok) "Model ready" else "Load failed",
                loadedModelId = if (ok) modelId else null
            ) }
        }
    }

    fun unloadModel() {
        repo.unloadModel()
        _uiState.update { it.copy(isModelLoaded = false, loadedModelId = null) }
    }

    // ── Inference ─────────────────────────────────────────────────────────────

    fun sendMessage(userInput: String) {
        if (!_uiState.value.isModelLoaded) {
            _uiState.update { it.copy(errorMessage = "Load a model first") }
            return
        }
        if (_uiState.value.isGenerating) return

        val userMsg = ChatMessageUi(role = "user", content = userInput)
        _chatMessages.update { it + userMsg }
        _streamBuffer.value = ""

        val assistantPlaceholder = ChatMessageUi(role = "assistant", content = "", isStreaming = true)
        _chatMessages.update { it + assistantPlaceholder }

        _uiState.update { it.copy(isGenerating = true, errorMessage = null) }

        val prompt = buildPrompt(inferenceConfig.systemPrompt, userInput)

        viewModelScope.launch(Dispatchers.IO) {
            val tokenBuffer = StringBuilder()

            repo.generate(
                config = inferenceConfig,
                prompt = prompt,
                onToken = { token ->
                    tokenBuffer.append(token)
                    _streamBuffer.value = tokenBuffer.toString()
                    _chatMessages.update { msgs ->
                        msgs.dropLast(1) + assistantPlaceholder.copy(
                            content = tokenBuffer.toString(),
                            isStreaming = true
                        )
                    }
                },
                onDone = { stats ->
                    val finalMsg = assistantPlaceholder.copy(
                        content = tokenBuffer.toString(),
                        isStreaming = false,
                        stats = stats
                    )
                    _chatMessages.update { msgs -> msgs.dropLast(1) + finalMsg }
                    _uiState.update { it.copy(isGenerating = false) }
                    _streamBuffer.value = ""
                },
                onError = { err ->
                    _chatMessages.update { it.dropLast(1) }
                    _uiState.update { it.copy(
                        isGenerating = false,
                        errorMessage = err
                    ) }
                }
            )
        }
    }

    fun cancelGeneration() {
        repo.cancelGeneration()
        _uiState.update { it.copy(isGenerating = false) }
        _chatMessages.update { msgs ->
            msgs.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
        }
    }

    fun clearChat() {
        if (!_uiState.value.isGenerating) {
            _chatMessages.value = emptyList()
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────

    fun updateConfig(
        temperature: Float? = null,
        topP: Float? = null,
        maxNewTokens: Int? = null,
        nCtx: Int? = null,
        nGpuLayers: Int? = null,
        systemPrompt: String? = null
    ) {
        inferenceConfig = inferenceConfig.copy(
            temperature = temperature ?: inferenceConfig.temperature,
            topP = topP ?: inferenceConfig.topP,
            maxNewTokens = maxNewTokens ?: inferenceConfig.maxNewTokens,
            nCtx = nCtx ?: inferenceConfig.nCtx,
            nGpuLayers = nGpuLayers ?: inferenceConfig.nGpuLayers,
            systemPrompt = systemPrompt ?: inferenceConfig.systemPrompt
        )
    }

    // ── Benchmark ─────────────────────────────────────────────────────────────

    fun runBenchmark() {
        if (!_uiState.value.isModelLoaded) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Running benchmark...") }
            val json = repo.runBenchmark(64)
            _uiState.update { it.copy(isLoading = false, statusMessage = "Benchmark complete") }
            // Parse and store
            if (json != null) {
                try {
                    val j = org.json.JSONObject(json)
                    _benchmarkResults.value = listOf(
                        BenchmarkResult(
                            name = "Generation Speed",
                            backend = j.optString("backend", "Unknown"),
                            score = j.optDouble("gen_tps", 0.0).toFloat(),
                            timeMs = j.optLong("total_ms")
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPrompt(system: String, user: String): String {
        // Use the model's family template
        val model = models.value.firstOrNull { it.id == inferenceConfig.modelId }
        return when (model?.family) {
            "llama3" ->
                "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$system" +
                "<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n$user" +
                "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
            "gemma2", "gemma" ->
                "<bos><start_of_turn>user\n$user<end_of_turn>\n<start_of_turn>model\n"
            "phi3" ->
                "<|system|>\n$system<|end|>\n<|user|>\n$user<|end|>\n<|assistant|>\n"
            "qwen2.5" ->
                "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
            else -> "$system\n\nUser: $user\nAssistant:"
        }
    }
}

data class UiState(
    val isLoading: Boolean = false,
    val isModelLoaded: Boolean = false,
    val isGenerating: Boolean = false,
    val loadedModelId: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val downloadProgress: Pair<String, Float>? = null
)

data class ChatMessageUi(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val stats: InferenceStats? = null
)
