/**
 * ExynNix — CPU Backend (llama.cpp XNNPACK / ARM NEON)
 * API-compatible with llama.cpp b8648+
 */

#include "cpu_backend.h"
#include <android/log.h>
#include <sched.h>
#include <chrono>
#include <thread>
#include <sstream>
#include <vector>

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
    llama_model*   model   = nullptr;
    llama_context* ctx     = nullptr;
    llama_sampler* sampler = nullptr;
    const llama_vocab* vocab = nullptr;
#endif
    InferenceConfig config;
};

CpuBackend::CpuBackend()  : impl_(std::make_unique<Impl>()) {}
CpuBackend::~CpuBackend() { unload(); }

int CpuBackend::getOptimalThreadCount() {
    return 4;  // big+mid cluster (Cortex-A77 x4)
}

bool CpuBackend::load(const InferenceConfig& config) {
    impl_->config = config;
#ifdef HAVE_LLAMA_CPP
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap     = config.use_mmap;
    mparams.use_mlock    = config.use_mlock;

    LOGI("Loading: %s", config.model_path.c_str());
    impl_->model = llama_model_load_from_file(config.model_path.c_str(), mparams);
    if (!impl_->model) { LOGE("Model load failed"); return false; }

    impl_->vocab = llama_model_get_vocab(impl_->model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx          = config.n_ctx;
    cparams.n_batch        = config.n_batch;
    cparams.n_threads      = getOptimalThreadCount();
    cparams.n_threads_batch = getOptimalThreadCount();

    impl_->ctx = llama_new_context_with_model(impl_->model, cparams);
    if (!impl_->ctx) {
        llama_model_free(impl_->model); impl_->model = nullptr;
        return false;
    }

    auto sparams    = llama_sampler_chain_default_params();
    impl_->sampler  = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(impl_->sampler, llama_sampler_init_temp(config.temperature));
    llama_sampler_chain_add(impl_->sampler, llama_sampler_init_top_p(config.top_p, 1));
    llama_sampler_chain_add(impl_->sampler, llama_sampler_init_dist(42));

    LOGI("CPU model ready. ctx=%d threads=%d", config.n_ctx, getOptimalThreadCount());
    loaded_ = true;
    return true;
#else
    LOGI("Stub mode (no llama.cpp): %s", config.model_path.c_str());
    loaded_ = true;
    return true;
#endif
}

void CpuBackend::unload() {
#ifdef HAVE_LLAMA_CPP
    if (impl_->sampler) { llama_sampler_free(impl_->sampler);    impl_->sampler = nullptr; }
    if (impl_->ctx)     { llama_free(impl_->ctx);                impl_->ctx     = nullptr; }
    if (impl_->model)   { llama_model_free(impl_->model);        impl_->model   = nullptr; }
    llama_backend_free();
#endif
    loaded_ = false;
}

bool CpuBackend::isLoaded() const { return loaded_; }

void CpuBackend::generate(const std::string& prompt, TokenCallback cb, InferenceStats& stats) {
    if (!loaded_) { cb("[Model not loaded]", true); return; }
    cancelled.store(false);
    auto t_start = std::chrono::steady_clock::now();

#ifdef HAVE_LLAMA_CPP
    // Tokenize using vocab (new API: llama_vocab* instead of llama_model*)
    std::vector<llama_token> tokens(prompt.size() + 32);
    int n = llama_tokenize(impl_->vocab,
                           prompt.c_str(), (int32_t)prompt.size(),
                           tokens.data(), (int32_t)tokens.size(),
                           /*add_special=*/true, /*parse_special=*/true);
    if (n < 0) { cb("[Tokenize failed]", true); return; }
    tokens.resize(n);
    stats.prompt_tokens = n;

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(impl_->ctx, batch) != 0) { cb("[Decode failed]", true); return; }

    auto t_first = std::chrono::steady_clock::now();
    stats.time_to_first_token_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        t_first - t_start).count();

    int generated = 0;
    while (!cancelled.load() && generated < impl_->config.max_new_tokens) {
        llama_token tok = llama_sampler_sample(impl_->sampler, impl_->ctx, -1);

        // Use new API: llama_vocab_is_eog
        if (llama_vocab_is_eog(impl_->vocab, tok)) { cb("", true); break; }

        char buf[256]{};
        // New API: llama_token_to_piece takes llama_vocab*
        int nc = llama_token_to_piece(impl_->vocab, tok, buf, sizeof(buf), 0, true);
        if (nc > 0) cb(std::string(buf, nc), false);

        llama_batch nb = llama_batch_get_one(&tok, 1);
        if (llama_decode(impl_->ctx, nb) != 0) break;
        generated++;
    }

    auto t_end = std::chrono::steady_clock::now();
    long ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    stats.generated_tokens   = generated;
    stats.total_time_ms      = ms;
    stats.gen_tokens_per_sec = ms > 0 ? generated * 1000.0f / ms : 0;
    stats.backend_used       = Backend::CPU_XNNPACK;
    if (cancelled.load()) cb("", true);
#else
    std::string r = "ExynNix stub: add llama.cpp submodule and rebuild for real inference.";
    for (char c : r) {
        if (cancelled.load()) break;
        cb(std::string(1, c), false);
        std::this_thread::sleep_for(std::chrono::milliseconds(15));
    }
    cb("", true);
    stats.gen_tokens_per_sec = 25.0f;
    stats.backend_used = Backend::CPU_XNNPACK;
#endif
}

void CpuBackend::cancel() { cancelled.store(true); }

} // namespace exynix
