/**
 * ExynNix — CPU Backend (XNNPACK / llama.cpp)
 *
 * For the Exynos 990 the optimal CPU config is:
 *   - 4 threads on big+mid cluster (CPU 0-3, Cortex-A77)
 *   - Avoid little cluster (A55) for inference — high latency
 *   - Enable mmap for model files (reduces RSS significantly)
 *   - Batch size 512 for prompt processing
 *
 * NOTE: llama.cpp source must be placed at:
 *   app/src/main/cpp/llama/  (git submodule or copy)
 *   with llama.h and llama.cpp present.
 *   The cmake will add_subdirectory(llama) when found.
 */

#include "cpu_backend.h"
#include <android/log.h>
#include <sched.h>
#include <sys/resource.h>
#include <chrono>
#include <thread>
#include <sstream>

// Conditionally include llama.cpp if available
#if __has_include(<llama.h>)
#define HAVE_LLAMA_CPP 1
#include <llama.h>
#endif

#define LOG_TAG "ExynNix-CPU"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace exynix {

struct CpuBackend::Impl {
#ifdef HAVE_LLAMA_CPP
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    llama_sampler* sampler = nullptr;
#else
    void* model = nullptr;
    void* ctx = nullptr;
#endif
    InferenceConfig config;
};

CpuBackend::CpuBackend() : impl_(std::make_unique<Impl>()) {}
CpuBackend::~CpuBackend() { unload(); }

int CpuBackend::getOptimalThreadCount() {
    // Cortex-A77 big+mid = 4 cores for inference
    // Never use all 8 — thermal throttling kicks in fast
    return 4;
}

void CpuBackend::setThreadAffinity(int thread_id, int core_id) {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(core_id, &cpuset);
    // Pin to big cluster (cores 4-7 on Exynos 990 typically)
    // Actual mapping depends on kernel scheduler
    sched_setaffinity(0, sizeof(cpuset), &cpuset);
}

bool CpuBackend::load(const InferenceConfig& config) {
    impl_->config = config;

#ifdef HAVE_LLAMA_CPP
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // CPU-only
    mparams.use_mmap = config.use_mmap;
    mparams.use_mlock = config.use_mlock;

    LOGI("Loading GGUF model: %s", config.model_path.c_str());
    impl_->model = llama_load_model_from_file(config.model_path.c_str(), mparams);

    if (!impl_->model) {
        LOGE("Failed to load model from: %s", config.model_path.c_str());
        return false;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = config.n_ctx;
    cparams.n_batch = config.n_batch;
    cparams.n_threads = getOptimalThreadCount();
    cparams.n_threads_batch = getOptimalThreadCount();
    cparams.type_k = GGML_TYPE_Q8_0;   // Quantized KV cache to save RAM
    cparams.type_v = GGML_TYPE_Q8_0;

    impl_->ctx = llama_new_context_with_model(impl_->model, cparams);
    if (!impl_->ctx) {
        LOGE("Failed to create llama context");
        llama_free_model(impl_->model);
        impl_->model = nullptr;
        return false;
    }

    // Build sampler chain
    auto sparams = llama_sampler_chain_default_params();
    impl_->sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(impl_->sampler, llama_sampler_init_temp(config.temperature));
    llama_sampler_chain_add(impl_->sampler, llama_sampler_init_top_p(config.top_p, 1));
    llama_sampler_chain_add(impl_->sampler, llama_sampler_init_dist(42));

    LOGI("Model loaded OK. Context=%d Threads=%d", config.n_ctx, getOptimalThreadCount());
    loaded_ = true;
    return true;
#else
    // Stub — model will show as loaded for UI testing without llama.cpp
    LOGI("llama.cpp not compiled in — running in stub mode for: %s", config.model_path.c_str());
    loaded_ = true;
    return true;
#endif
}

void CpuBackend::unload() {
#ifdef HAVE_LLAMA_CPP
    if (impl_->sampler) { llama_sampler_free(impl_->sampler); impl_->sampler = nullptr; }
    if (impl_->ctx)     { llama_free(impl_->ctx);             impl_->ctx = nullptr; }
    if (impl_->model)   { llama_free_model(impl_->model);     impl_->model = nullptr; }
    llama_backend_free();
#endif
    loaded_ = false;
}

bool CpuBackend::isLoaded() const { return loaded_; }

void CpuBackend::generate(const std::string& prompt, TokenCallback cb, InferenceStats& stats) {
    if (!loaded_) { cb("[ERROR: Model not loaded]", true); return; }

    auto t_start = std::chrono::steady_clock::now();
    cancelled.store(false);

#ifdef HAVE_LLAMA_CPP
    // Tokenize prompt
    const auto tokens = [&]() {
        std::vector<llama_token> toks(prompt.size() + 16);
        int n = llama_tokenize(impl_->model, prompt.c_str(), prompt.size(),
                               toks.data(), toks.size(), /*add_bos=*/true, /*special=*/true);
        toks.resize(n);
        return toks;
    }();

    stats.prompt_tokens = tokens.size();

    // Eval prompt
    llama_batch batch = llama_batch_get_one(const_cast<llama_token*>(tokens.data()), tokens.size());
    auto t_prompt_start = std::chrono::steady_clock::now();
    if (llama_decode(impl_->ctx, batch) != 0) {
        cb("[ERROR: llama_decode failed]", true);
        return;
    }
    auto t_first_token = std::chrono::steady_clock::now();
    stats.time_to_first_token_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        t_first_token - t_start).count();

    // Generate
    int n_generated = 0;
    while (!cancelled.load() && n_generated < impl_->config.max_new_tokens) {
        llama_token token = llama_sampler_sample(impl_->sampler, impl_->ctx, -1);

        if (llama_token_is_eog(impl_->model, token)) {
            cb("", true);
            break;
        }

        char buf[256] = {};
        int n = llama_token_to_piece(impl_->model, token, buf, sizeof(buf), 0, true);
        if (n < 0) continue;

        std::string piece(buf, n);
        cb(piece, false);

        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(impl_->ctx, next) != 0) break;
        n_generated++;
    }

    auto t_end = std::chrono::steady_clock::now();
    long total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();

    stats.generated_tokens = n_generated;
    stats.total_time_ms = total_ms;
    stats.gen_tokens_per_sec = (total_ms > 0) ? (n_generated * 1000.0f / total_ms) : 0;
    stats.backend_used = Backend::CPU_XNNPACK;
    stats.prompt_tokens_per_sec = (total_ms > 0) ? (stats.prompt_tokens * 1000.0f / total_ms) : 0;

    if (cancelled.load()) cb("", true);

#else
    // Stub generation for testing UI without llama.cpp
    std::string response =
        "ExynNix stub response: llama.cpp not compiled. "
        "Place llama.cpp source at app/src/main/cpp/llama/ and rebuild. "
        "This stub confirms the JNI bridge, backend router, and UI are all working correctly.";

    for (char c : response) {
        if (cancelled.load()) break;
        cb(std::string(1, c), false);
        std::this_thread::sleep_for(std::chrono::milliseconds(15));
    }
    cb("", true);

    stats.generated_tokens = response.size();
    stats.gen_tokens_per_sec = 25.0f;  // placeholder
    stats.backend_used = Backend::CPU_XNNPACK;
    stats.total_time_ms = response.size() * 15;
#endif
}

void CpuBackend::cancel() {
    cancelled.store(true);
}

} // namespace exynix
