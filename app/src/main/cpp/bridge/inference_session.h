#pragma once
#include <string>
#include <vector>
#include <functional>
#include <memory>
#include <atomic>

namespace exynix {

enum class Backend {
    CPU_XNNPACK,
    GPU_VULKAN,
    NNAPI_GPU,
    NNAPI_NPU,
    AUTO
};

enum class ModelFormat {
    GGUF,       // llama.cpp format
    PTE,        // ExecuTorch .pte
    ONNX,       // ONNX Runtime
    TFLITE,     // TensorFlow Lite
    UNKNOWN
};

struct InferenceConfig {
    std::string model_path;
    Backend backend = Backend::AUTO;
    ModelFormat format = ModelFormat::UNKNOWN;

    // Generation params
    int n_ctx = 2048;
    int n_threads = 4;         // Use mid+little cluster for efficiency
    int n_gpu_layers = 0;      // For gguf: layers offloaded to Vulkan
    int n_batch = 512;
    float temperature = 0.7f;
    float top_p = 0.9f;
    int max_new_tokens = 256;
    bool use_mmap = true;
    bool use_mlock = false;

    // Quantization hint (for GGUF)
    // Q4_0, Q4_K_M, Q5_K_M, Q8_0, F16
    std::string quantization = "Q4_K_M";
};

struct InferenceStats {
    float prompt_tokens_per_sec = 0.0f;
    float gen_tokens_per_sec = 0.0f;
    int prompt_tokens = 0;
    int generated_tokens = 0;
    long time_to_first_token_ms = 0;
    long total_time_ms = 0;
    Backend backend_used;
    float peak_cpu_temp = 0.0f;
    long peak_mem_usage_mb = 0;
};

// Token callback — called with each generated token
using TokenCallback = std::function<void(const std::string& token, bool is_done)>;

class InferenceSession {
public:
    virtual ~InferenceSession() = default;

    virtual bool load(const InferenceConfig& config) = 0;
    virtual void unload() = 0;
    virtual bool isLoaded() const = 0;

    virtual void generate(
        const std::string& prompt,
        TokenCallback callback,
        InferenceStats& stats_out
    ) = 0;

    virtual void cancel() = 0;
    virtual std::string backendName() const = 0;

    std::atomic<bool> cancelled{false};
};

// Factory
std::unique_ptr<InferenceSession> createSession(Backend backend);

// Auto-selects best backend for the device
Backend autoSelectBackend(const std::string& model_path);

} // namespace exynix
