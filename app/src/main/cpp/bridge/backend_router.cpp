/**
 * ExynNix — Backend Router
 * Routes inference requests to the best available hardware backend.
 *
 * Priority order for Exynos 990:
 * 1. Vulkan (Mali-G77 MP11) — for large GGUF models with layer offloading
 * 2. NNAPI GPU delegate     — for TFLite/ONNX models
 * 3. NNAPI NPU delegate     — limited ops set but very low power
 * 4. CPU XNNPACK            — fallback, Cortex-A77 dotprod, always works
 */

#include "backend_router.h"
#include "hardware_probe.h"
#include "inference_session.h"
#include "cpu_backend.h"
#include "vulkan_backend.h"
#include "nnapi_backend.h"
#include <android/log.h>
#include <fstream>
#include <algorithm>

#define LOG_TAG "ExynNix-Router"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace exynix {

static HardwareProfile g_profile;
static bool g_profile_ready = false;

const HardwareProfile& getCachedProfile() {
    if (!g_profile_ready) {
        g_profile = probeAll();
        g_profile_ready = true;
    }
    return g_profile;
}

ModelFormat detectFormat(const std::string& path) {
    if (path.size() >= 5 && path.substr(path.size() - 5) == ".gguf") return ModelFormat::GGUF;
    if (path.size() >= 4 && path.substr(path.size() - 4) == ".pte") return ModelFormat::PTE;
    if (path.size() >= 5 && path.substr(path.size() - 5) == ".onnx") return ModelFormat::ONNX;
    if (path.size() >= 8 && path.substr(path.size() - 8) == ".tflite") return ModelFormat::TFLITE;
    // Read magic bytes
    std::ifstream f(path, std::ios::binary);
    if (f) {
        char magic[8] = {};
        f.read(magic, 8);
        // GGUF magic: 0x46554747 = "GGUF"
        if (magic[0]=='G' && magic[1]=='G' && magic[2]=='U' && magic[3]=='F') return ModelFormat::GGUF;
        // TFLite flatbuffer magic
        if (magic[4]=='T' && magic[5]=='F' && magic[6]=='L' && magic[7]=='3') return ModelFormat::TFLITE;
    }
    return ModelFormat::UNKNOWN;
}

Backend autoSelectBackend(const std::string& model_path) {
    const auto& profile = getCachedProfile();
    ModelFormat fmt = detectFormat(model_path);

    if (fmt == ModelFormat::GGUF) {
        // GGUF → prefer Vulkan layer offloading on Mali-G77
        if (profile.gpu.available && profile.gpu.is_mali) {
            LOGI("GGUF → Vulkan (Mali-G77 layer offload)");
            return Backend::GPU_VULKAN;
        }
    }

    if (fmt == ModelFormat::TFLITE || fmt == ModelFormat::ONNX) {
        // Delegate models → try NNAPI NPU first, then GPU
        if (profile.nnapi.has_npu) {
            LOGI("TFLite/ONNX → NNAPI NPU");
            return Backend::NNAPI_NPU;
        }
        if (profile.nnapi.has_gpu) {
            LOGI("TFLite/ONNX → NNAPI GPU");
            return Backend::NNAPI_GPU;
        }
    }

    // PTE → ExecuTorch XNNPACK + Vulkan
    if (fmt == ModelFormat::PTE) {
        if (profile.gpu.available) {
            LOGI("PTE → Vulkan");
            return Backend::GPU_VULKAN;
        }
    }

    // Fallback
    LOGI("Falling back to CPU XNNPACK");
    return Backend::CPU_XNNPACK;
}

std::unique_ptr<InferenceSession> createSession(Backend backend) {
    switch (backend) {
        case Backend::GPU_VULKAN:
            return std::make_unique<VulkanBackend>();
        case Backend::NNAPI_GPU:
        case Backend::NNAPI_NPU:
            return std::make_unique<NnapiBackend>(backend);
        case Backend::CPU_XNNPACK:
        default:
            return std::make_unique<CpuBackend>();
    }
}

// Profile the model file size to recommend quantization
std::string recommendQuantization(long model_size_mb, long avail_ram_mb) {
    if (avail_ram_mb <= 0) avail_ram_mb = 4096;  // conservative default
    long budget = avail_ram_mb * 8 / 10;  // use 80% of available RAM

    // Size ranges for ~7B model at different quants:
    // Q4_0 ~3.9GB, Q4_K_M ~4.1GB, Q5_K_M ~4.8GB, Q8_0 ~7GB, F16 ~14GB
    if (model_size_mb < 2000) return "Q8_0";   // small model, full quality
    if (model_size_mb < 4500 && budget > 4096) return "Q4_K_M";
    if (model_size_mb < 5000 && budget > 5000) return "Q5_K_M";
    return "Q4_0";  // safest for 12GB RAM device with other apps running
}

} // namespace exynix
