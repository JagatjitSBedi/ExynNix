/**
 * ExynNix — Hardware Probe
 * Detects and reports S20 Ultra Exynos 990 hardware capabilities at runtime.
 *
 * Hardware map for Exynos 990:
 *   CPU   : 2x Cortex-A77 (big) @ 2.73 GHz
 *           2x Cortex-A77 (mid) @ 2.50 GHz
 *           4x Cortex-A55 (little) @ 2.0 GHz
 *   GPU   : Mali-G77 MP11 — Vulkan 1.1, OpenCL 2.0
 *   NPU   : Samsung 2-core NPU (not exposed via NNAPI on Exynos 990)
 *   DSP   : Hexagon-like DSP (accessible via NNAPI when delegated)
 *   RAM   : 12 GB LPDDR5
 */

#include "hardware_probe.h"
#include <android/log.h>
#include <android/hardware_buffer.h>
#include <sys/sysinfo.h>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <dlfcn.h>

#ifdef EXYNIX_NNAPI
#if __has_include(<NeuralNetworks.h>)
#include <NeuralNetworks.h>
#else
#undef EXYNIX_NNAPI
#endif
#endif

#ifdef EXYNIX_VULKAN
#if __has_include(<vulkan/vulkan.h>)
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>
#else
#undef EXYNIX_VULKAN
#endif
#endif

#define LOG_TAG "ExynNix-HWProbe"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace exynix {

// ────────────────────────────────────────────────────────────────────────────
// CPU Info
// ────────────────────────────────────────────────────────────────────────────
CpuInfo probeCpu() {
    CpuInfo info{};
    info.core_count = 0;
    info.big_cores = 2;
    info.mid_cores = 2;
    info.little_cores = 4;
    info.arch = "ARMv8.2-A";
    info.has_dotprod = true;    // Cortex-A77 feature
    info.has_fp16 = true;
    info.has_neon = true;
    info.has_sve = false;       // SVE is Cortex-A78+ only

    // Read actual core count from sysfs
    std::ifstream possible("/sys/devices/system/cpu/possible");
    if (possible.is_open()) {
        std::string line;
        std::getline(possible, line);
        info.topology_str = line;
    }

    // Max frequency for big cluster
    std::ifstream freq("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
    if (freq.is_open()) {
        freq >> info.max_freq_khz;
    }

    // Available RAM
    struct sysinfo si;
    if (sysinfo(&si) == 0) {
        info.total_ram_mb = (si.totalram * si.mem_unit) / (1024 * 1024);
        info.avail_ram_mb = (si.freeram * si.mem_unit) / (1024 * 1024);
    }

    LOGI("CPU: %s | cores=%d+%d+%d | dotprod=%d | fp16=%d | RAM=%dMB",
         info.arch.c_str(), info.big_cores, info.mid_cores, info.little_cores,
         info.has_dotprod, info.has_fp16, info.total_ram_mb);
    return info;
}

// ────────────────────────────────────────────────────────────────────────────
// Vulkan / Mali-G77 GPU Info
// ────────────────────────────────────────────────────────────────────────────
GpuInfo probeGpu() {
    GpuInfo info{};
    info.available = false;

#ifdef EXYNIX_VULKAN
    VkInstance instance = VK_NULL_HANDLE;
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "ExynNix";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        LOGE("Vulkan instance creation failed: %d", result);
        info.error = "Vulkan instance creation failed";
        return info;
    }

    uint32_t deviceCount = 0;
    vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);

    if (deviceCount == 0) {
        LOGE("No Vulkan physical devices found");
        vkDestroyInstance(instance, nullptr);
        return info;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());

    VkPhysicalDeviceProperties props{};
    vkGetPhysicalDeviceProperties(devices[0], &props);

    VkPhysicalDeviceMemoryProperties memProps{};
    vkGetPhysicalDeviceMemoryProperties(devices[0], &memProps);

    info.available = true;
    info.device_name = props.deviceName;
    info.vendor_id = props.vendorID;
    info.driver_version = props.driverVersion;
    info.api_version = props.apiVersion;

    // Mali-G77 vendor ID = 0x13B5 (ARM)
    info.is_mali = (props.vendorID == 0x13B5);

    // Total device-accessible memory
    info.total_vram_mb = 0;
    for (uint32_t i = 0; i < memProps.memoryHeapCount; i++) {
        if (memProps.memoryHeaps[i].flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) {
            info.total_vram_mb += memProps.memoryHeaps[i].size / (1024 * 1024);
        }
    }

    // Compute shader capabilities
    VkPhysicalDeviceFeatures features{};
    vkGetPhysicalDeviceFeatures(devices[0], &features);
    info.supports_fp16_compute = (features.shaderFloat64 == VK_FALSE); // MALI FP16 heuristic
    info.supports_int8_compute = true;  // Mali-G77 supports int8 via subgroup extensions

    vkDestroyInstance(instance, nullptr);

    LOGI("GPU: %s | vendor=0x%X | mali=%d | vram=%dMB | fp16=%d",
         info.device_name.c_str(), info.vendor_id,
         info.is_mali, info.total_vram_mb, info.supports_fp16_compute);
#else
    info.error = "Vulkan not compiled in";
    LOGI("Vulkan backend not compiled");
#endif
    return info;
}

// ────────────────────────────────────────────────────────────────────────────
// NNAPI Info (CPU/GPU/NPU delegate)
// ────────────────────────────────────────────────────────────────────────────
NnapiInfo probeNnapi() {
    NnapiInfo info{};
    info.available = false;

#ifdef EXYNIX_NNAPI
    // Check NNAPI version
    int32_t version = 0;
    ANeuralNetworks_getRuntimeFeatureLevel(&version);
    info.runtime_feature_level = version;

    // Enumerate devices
    uint32_t deviceCount = 0;
    ANeuralNetworks_getDeviceCount(&deviceCount);
    info.device_count = deviceCount;

    for (uint32_t i = 0; i < deviceCount; i++) {
        ANeuralNetworksDevice* device;
        ANeuralNetworks_getDevice(i, &device);

        const char* name;
        ANeuralNetworksDevice_getName(device, &name);

        int32_t type;
        ANeuralNetworksDevice_getType(device, &type);

        int64_t featureLevel;
        ANeuralNetworksDevice_getFeatureLevel(device, &featureLevel);

        NnapiDevice dev{};
        dev.name = name ? name : "unknown";
        dev.type = type;
        dev.feature_level = featureLevel;

        // Detect NPU/DSP type
        switch (type) {
            case ANEURALNETWORKS_DEVICE_ACCELERATOR:
                dev.type_str = "NPU/DSP Accelerator";
                info.has_npu = true;
                break;
            case ANEURALNETWORKS_DEVICE_GPU:
                dev.type_str = "GPU";
                info.has_gpu = true;
                break;
            case ANEURALNETWORKS_DEVICE_CPU:
                dev.type_str = "CPU Reference";
                info.has_cpu = true;
                break;
            default:
                dev.type_str = "Unknown";
        }

        info.devices.push_back(dev);
        LOGI("NNAPI device[%d]: %s | type=%s | featureLevel=%lld",
             i, dev.name.c_str(), dev.type_str.c_str(), (long long)featureLevel);
    }

    info.available = (deviceCount > 0);
    LOGI("NNAPI: available=%d | devices=%d | npu=%d | gpu=%d | cpu=%d",
         info.available, info.device_count, info.has_npu, info.has_gpu, info.has_cpu);
#else
    info.error = "NNAPI not compiled in";
#endif
    return info;
}

// ────────────────────────────────────────────────────────────────────────────
// Full hardware probe
// ────────────────────────────────────────────────────────────────────────────
HardwareProfile probeAll() {
    HardwareProfile profile{};
    profile.soc_name = "Samsung Exynos 990";
    profile.device_model = "Samsung Galaxy S20 Ultra";
    profile.target_abi = "arm64-v8a";

    profile.cpu = probeCpu();
    profile.gpu = probeGpu();
    profile.nnapi = probeNnapi();

    // Determine best available backend
    if (profile.gpu.available && profile.gpu.is_mali) {
        profile.recommended_backend = "VULKAN";
        profile.recommended_backend_reason = "Mali-G77 MP11 Vulkan — best throughput for batched inference";
    } else if (profile.nnapi.available && profile.nnapi.has_npu) {
        profile.recommended_backend = "NNAPI_NPU";
        profile.recommended_backend_reason = "NNAPI NPU delegate — lowest latency for supported ops";
    } else if (profile.nnapi.available && profile.nnapi.has_gpu) {
        profile.recommended_backend = "NNAPI_GPU";
        profile.recommended_backend_reason = "NNAPI GPU delegate";
    } else {
        profile.recommended_backend = "CPU_XNNPACK";
        profile.recommended_backend_reason = "XNNPACK CPU — Cortex-A77 NEON + dotprod optimized";
    }

    LOGI("Recommended backend: %s", profile.recommended_backend.c_str());
    return profile;
}

// ────────────────────────────────────────────────────────────────────────────
// Thermal monitoring
// ────────────────────────────────────────────────────────────────────────────
float readCpuTemperature() {
    const char* thermal_paths[] = {
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/class/thermal/thermal_zone1/temp",
        "/sys/devices/virtual/thermal/thermal_zone0/temp"
    };
    for (auto& path : thermal_paths) {
        std::ifstream f(path);
        if (f.is_open()) {
            int temp;
            f >> temp;
            return temp / 1000.0f;  // milli-celsius to celsius
        }
    }
    return -1.0f;
}

} // namespace exynix
