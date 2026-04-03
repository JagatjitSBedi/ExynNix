/**
 * ExynNix — NNAPI Backend
 * Delegates computation to NNAPI device (GPU or NPU on Exynos 990).
 */

#include "nnapi_backend.h"
#include <android/log.h>
#include <chrono>
#include <thread>
#include <sstream>

#ifdef EXYNIX_NNAPI
#include <NeuralNetworks.h>
#endif

#define LOG_TAG "ExynNix-NNAPI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace exynix {

struct NnapiBackend::Impl {
    InferenceConfig config;
    Backend mode;
#ifdef EXYNIX_NNAPI
    ANeuralNetworksCompilation* compilation = nullptr;
    ANeuralNetworksModel* model_nn = nullptr;
#endif
};

NnapiBackend::NnapiBackend(Backend mode) : mode_(mode), impl_(std::make_unique<Impl>()) {
    impl_->mode = mode;
}
NnapiBackend::~NnapiBackend() { unload(); }

std::string NnapiBackend::backendName() const {
    return mode_ == Backend::NNAPI_NPU
        ? "NNAPI NPU (Exynos 990 accelerator)"
        : "NNAPI GPU (Mali-G77 OpenCL delegate)";
}

bool NnapiBackend::load(const InferenceConfig& config) {
    impl_->config = config;

#ifdef EXYNIX_NNAPI
    // For GGUF models, NNAPI is not directly applicable.
    // NNAPI works best with TFLite/ONNX models.
    // This backend handles TFLite inference with device selection.
    if (config.format != ModelFormat::TFLITE && config.format != ModelFormat::ONNX) {
        LOGE("NNAPI backend requires TFLite or ONNX format, got GGUF/PTE");
        LOGI("Tip: Convert model with: python -m tf2onnx.convert --saved-model model --output model.onnx");
        return false;
    }

    uint32_t deviceCount;
    ANeuralNetworks_getDeviceCount(&deviceCount);

    ANeuralNetworksDevice* targetDevice = nullptr;
    for (uint32_t i = 0; i < deviceCount; i++) {
        ANeuralNetworksDevice* device;
        ANeuralNetworks_getDevice(i, &device);
        int32_t type;
        ANeuralNetworksDevice_getType(device, &type);

        bool wantNpu = (mode_ == Backend::NNAPI_NPU) &&
                       (type == ANEURALNETWORKS_DEVICE_ACCELERATOR);
        bool wantGpu = (mode_ == Backend::NNAPI_GPU) &&
                       (type == ANEURALNETWORKS_DEVICE_GPU);

        if (wantNpu || wantGpu) {
            targetDevice = device;
            const char* name;
            ANeuralNetworksDevice_getName(device, &name);
            LOGI("Selected NNAPI device: %s", name);
            break;
        }
    }

    if (!targetDevice) {
        LOGE("Target NNAPI device not found for mode: %s", backendName().c_str());
        return false;
    }

    // Create NNAPI model from TFLite flatbuffer
    // (In a full integration, use TFLite Interpreter with NnapiDelegate)
    // Here we show the device selection approach
    LOGI("NNAPI backend initialized for: %s", config.model_path.c_str());
    loaded_ = true;
    return true;

#else
    LOGI("NNAPI not compiled in — stub mode");
    loaded_ = true;
    return true;
#endif
}

void NnapiBackend::unload() {
#ifdef EXYNIX_NNAPI
    if (impl_->compilation) {
        ANeuralNetworksCompilation_free(impl_->compilation);
        impl_->compilation = nullptr;
    }
    if (impl_->model_nn) {
        ANeuralNetworksModel_free(impl_->model_nn);
        impl_->model_nn = nullptr;
    }
#endif
    loaded_ = false;
}

bool NnapiBackend::isLoaded() const { return loaded_; }

void NnapiBackend::generate(const std::string& prompt, TokenCallback cb, InferenceStats& stats) {
    if (!loaded_) { cb("[NNAPI: Not loaded]", true); return; }
    cancelled.store(false);

    auto t_start = std::chrono::steady_clock::now();

    // Stub response — real TFLite inference would run here
    std::ostringstream oss;
    oss << "ExynNix [" << backendName() << " stub] — "
        << "NNAPI device selected. For TFLite/ONNX models, "
        << "this backend routes computation to " << backendName() << ". "
        << "Provide a .tflite model for real inference.";

    std::string r = oss.str();
    for (char c : r) {
        if (cancelled.load()) break;
        cb(std::string(1, c), false);
        std::this_thread::sleep_for(std::chrono::milliseconds(8));
    }
    cb("", true);

    auto t_end = std::chrono::steady_clock::now();
    long ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();
    stats.generated_tokens = r.size();
    stats.gen_tokens_per_sec = r.size() > 0 ? r.size() * 1000.0f / ms : 0;
    stats.backend_used = mode_;
    stats.total_time_ms = ms;
}

void NnapiBackend::cancel() { cancelled.store(true); }

} // namespace exynix
