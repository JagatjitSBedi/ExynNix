#pragma once
#include "inference_session.h"

namespace exynix {

/**
 * Vulkan Backend — Mali-G77 MP11 GPU acceleration.
 *
 * Strategy for Exynos 990:
 *   - For GGUF models: use llama.cpp Vulkan compute shaders
 *     Set n_gpu_layers to offload transformer blocks to GPU.
 *     Embed layer stays CPU (small). Projection stays CPU.
 *     Attention + FFN → Mali-G77 Vulkan compute.
 *
 *   - Optimal n_gpu_layers for different model sizes on 12GB device:
 *     7B  Q4_K_M: up to 32 layers (fits in shared GPU/system RAM)
 *     13B Q4_K_M: up to 20 layers
 *     3B  Q4_K_M: all layers (full GPU)
 *
 *   - fp16 arithmetic enabled via VK_KHR_shader_float16_int8
 *   - Subgroup operations for efficient reduction: yes (Mali-G77)
 */
class VulkanBackend : public InferenceSession {
public:
    VulkanBackend();
    ~VulkanBackend() override;

    bool load(const InferenceConfig& config) override;
    void unload() override;
    bool isLoaded() const override;
    void generate(const std::string& prompt, TokenCallback cb, InferenceStats& stats) override;
    void cancel() override;
    std::string backendName() const override { return "GPU Vulkan (Mali-G77 MP11)"; }

    // Mali-G77 specific: compute optimal GPU layer count based on free VRAM
    static int computeOptimalGpuLayers(long model_size_mb, long vram_mb);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
    bool loaded_ = false;
};

} // namespace exynix
