#pragma once
#include "inference_session.h"

namespace exynix {

/**
 * CPU Backend — uses XNNPACK + ARM NEON + dotprod
 * Integrates with llama.cpp for GGUF model inference.
 * Thread affinity: pins to big/mid cluster (CPU 0-3) for sustained performance.
 */
class CpuBackend : public InferenceSession {
public:
    CpuBackend();
    ~CpuBackend() override;

    bool load(const InferenceConfig& config) override;
    void unload() override;
    bool isLoaded() const override;
    void generate(const std::string& prompt, TokenCallback cb, InferenceStats& stats) override;
    void cancel() override;
    std::string backendName() const override { return "CPU (XNNPACK + ARM NEON/dotprod)"; }

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
    bool loaded_ = false;

    void setThreadAffinity(int thread_id, int core_id);
    int getOptimalThreadCount();
};

} // namespace exynix
