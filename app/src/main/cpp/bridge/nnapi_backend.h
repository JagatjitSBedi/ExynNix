#pragma once
#include "inference_session.h"

namespace exynix {

/**
 * NNAPI Backend — delegates to GPU or NPU through Android NNAPI.
 *
 * On Exynos 990:
 *   - GPU delegate: routes matmuls to Mali-G77 via OpenCL/Vulkan underneath
 *   - NPU delegate: Samsung's 2-core NPU — limited op set but very low watt
 *   - Supports: TFLite FlatBuffer models and ONNX via NNAPI shim
 *
 * Best for:
 *   - Quantized CNNs (image classification, detection)
 *   - TFLite models (MobileNet, EfficientNet, BERT-tiny)
 *   - Low-power background inference
 */
class NnapiBackend : public InferenceSession {
public:
    explicit NnapiBackend(Backend mode);  // NNAPI_GPU or NNAPI_NPU
    ~NnapiBackend() override;

    bool load(const InferenceConfig& config) override;
    void unload() override;
    bool isLoaded() const override;
    void generate(const std::string& prompt, TokenCallback cb, InferenceStats& stats) override;
    void cancel() override;
    std::string backendName() const override;

private:
    Backend mode_;
    struct Impl;
    std::unique_ptr<Impl> impl_;
    bool loaded_ = false;
};

} // namespace exynix
