package com.exynix.studio.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// ── Hardware profile ─────────────────────────────────────────────────────────

data class HardwareProfile(
    val socName: String = "Samsung Exynos 990",
    val deviceModel: String = "Galaxy S20 Ultra",
    val recommendedBackend: String = "GPU_VULKAN",
    val recommendedReason: String = "",
    val cpu: CpuInfo = CpuInfo(),
    val gpu: GpuInfo = GpuInfo(),
    val nnapi: NnapiInfo = NnapiInfo()
)

data class CpuInfo(
    val arch: String = "ARMv8.2-A",
    val bigCores: Int = 2,
    val midCores: Int = 2,
    val littleCores: Int = 4,
    val hasDotprod: Boolean = true,
    val hasFp16: Boolean = true,
    val totalRamMb: Long = 12288,
    val availRamMb: Long = 4096
)

data class GpuInfo(
    val available: Boolean = false,
    val deviceName: String = "Mali-G77 MP11",
    val isMali: Boolean = true,
    val totalVramMb: Long = 0,
    val supportsFp16: Boolean = true
)

data class NnapiInfo(
    val available: Boolean = false,
    val deviceCount: Int = 0,
    val hasNpu: Boolean = false,
    val hasGpu: Boolean = false,
    val hasCpu: Boolean = true
)

// ── Backend ───────────────────────────────────────────────────────────────────

enum class InferenceBackend(val label: String, val description: String, val color: Long) {
    CPU_XNNPACK("CPU / XNNPACK", "Cortex-A77 · NEON · dotprod · always available", 0xFF4CAF50),
    GPU_VULKAN("GPU / Vulkan", "Mali-G77 MP11 · 11 compute clusters · FP16", 0xFF2196F3),
    NNAPI_GPU("NNAPI GPU", "Mali-G77 via NNAPI delegate · TFLite/ONNX", 0xFF9C27B0),
    NNAPI_NPU("NNAPI NPU", "Samsung 2-core NPU · limited op-set · low power", 0xFFFF9800),
    AUTO("Auto (recommended)", "Picks best backend for your model format", 0xFF00BCD4);
}

// ── Models ────────────────────────────────────────────────────────────────────

enum class ModelFormat { GGUF, PTE, ONNX, TFLITE, UNKNOWN }

enum class ModelStatus { NOT_DOWNLOADED, DOWNLOADING, READY, LOADING, RUNNING, ERROR }

@Entity(tableName = "models")
data class ModelEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val family: String,           // llama3, gemma, mistral, phi, qwen, etc.
    val paramCount: String,       // "7B", "3B", "1.5B"
    val quantization: String,     // "Q4_K_M", "Q8_0", "F16"
    val format: String,           // "GGUF", "PTE", "TFLITE"
    val sizeBytes: Long = 0,
    val localPath: String = "",
    val downloadUrl: String = "",
    val description: String = "",
    val status: String = ModelStatus.NOT_DOWNLOADED.name,
    val downloadProgress: Float = 0f,
    val lastUsed: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),

    // Preferred backend for this model
    val preferredBackend: String = InferenceBackend.AUTO.name,

    // Benchmark results
    val benchGenTps: Float = 0f,
    val benchPromptTps: Float = 0f,
    val benchTtftMs: Long = 0
)

// ── Inference session ─────────────────────────────────────────────────────────

data class InferenceConfig(
    val modelId: String,
    val backend: InferenceBackend = InferenceBackend.AUTO,
    val nCtx: Int = 2048,
    val nThreads: Int = 4,
    val nGpuLayers: Int = -1,     // -1 = auto
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxNewTokens: Int = 512,
    val systemPrompt: String = "You are a helpful AI assistant running on-device on Samsung Exynos 990.",
    val chatTemplate: String = "llama3"
)

data class InferenceStats(
    val promptTps: Float = 0f,
    val genTps: Float = 0f,
    val promptTokens: Int = 0,
    val genTokens: Int = 0,
    val ttftMs: Long = 0,
    val totalMs: Long = 0,
    val backendUsed: String = "",
    val peakTemp: Float = 0f,
    val peakMemMb: Long = 0
)

// ── Chat ─────────────────────────────────────────────────────────────────────

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String,              // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val stats: String = ""         // JSON InferenceStats for assistant messages
)

// ── Benchmark ─────────────────────────────────────────────────────────────────

data class BenchmarkResult(
    val name: String,
    val backend: String,
    val score: Float,
    val timeMs: Long,
    val gflops: Double = 0.0,
    val bandwidthGbps: Double = 0.0
)

// ── Preloaded model catalog ───────────────────────────────────────────────────

object ModelCatalog {
    val recommended = listOf(
        ModelEntry(
            id = "llama3.2-3b-q4km",
            name = "Llama 3.2 3B Q4_K_M",
            family = "llama3",
            paramCount = "3B",
            quantization = "Q4_K_M",
            format = "GGUF",
            sizeBytes = 1_900_000_000L,
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            description = "Best choice for S20 Ultra — fast on CPU+Vulkan, fits in RAM",
            preferredBackend = "GPU_VULKAN"
        ),
        ModelEntry(
            id = "phi3-mini-4k-q4km",
            name = "Phi-3 Mini 4K Q4_K_M",
            family = "phi3",
            paramCount = "3.8B",
            quantization = "Q4_K_M",
            format = "GGUF",
            sizeBytes = 2_200_000_000L,
            downloadUrl = "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q4_K_M.gguf",
            description = "Microsoft Phi-3 — strong reasoning, 4K context",
            preferredBackend = "GPU_VULKAN"
        ),
        ModelEntry(
            id = "gemma2-2b-q4km",
            name = "Gemma 2 2B Q4_K_M",
            family = "gemma2",
            paramCount = "2B",
            quantization = "Q4_K_M",
            format = "GGUF",
            sizeBytes = 1_600_000_000L,
            downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            description = "Google Gemma 2 2B — fastest option on Exynos 990",
            preferredBackend = "GPU_VULKAN"
        ),
        ModelEntry(
            id = "qwen2.5-1.5b-q8",
            name = "Qwen 2.5 1.5B Q8_0",
            family = "qwen2.5",
            paramCount = "1.5B",
            quantization = "Q8_0",
            format = "GGUF",
            sizeBytes = 1_600_000_000L,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf",
            description = "Alibaba Qwen 2.5 — multilingual (EN/JP/HI), high quality Q8",
            preferredBackend = "CPU_XNNPACK"
        ),
        ModelEntry(
            id = "llama3-8b-q4km",
            name = "Llama 3 8B Q4_K_M",
            family = "llama3",
            paramCount = "8B",
            quantization = "Q4_K_M",
            format = "GGUF",
            sizeBytes = 4_700_000_000L,
            downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
            description = "Llama 3 8B — best quality, needs 6-8 GB RAM, use with Vulkan layers",
            preferredBackend = "GPU_VULKAN"
        )
    )
}
