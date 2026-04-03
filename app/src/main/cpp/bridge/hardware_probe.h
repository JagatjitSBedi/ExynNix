#pragma once
#include <string>
#include <vector>

namespace exynix {

struct CpuInfo {
    std::string arch;
    std::string topology_str;
    int core_count = 0;
    int big_cores = 0;
    int mid_cores = 0;
    int little_cores = 0;
    bool has_neon = false;
    bool has_dotprod = false;
    bool has_fp16 = false;
    bool has_sve = false;
    long max_freq_khz = 0;
    long total_ram_mb = 0;
    long avail_ram_mb = 0;
};

struct GpuInfo {
    bool available = false;
    bool is_mali = false;
    bool supports_fp16_compute = false;
    bool supports_int8_compute = false;
    std::string device_name;
    std::string error;
    uint32_t vendor_id = 0;
    uint32_t driver_version = 0;
    uint32_t api_version = 0;
    long total_vram_mb = 0;
};

struct NnapiDevice {
    std::string name;
    std::string type_str;
    int32_t type = 0;
    int64_t feature_level = 0;
};

struct NnapiInfo {
    bool available = false;
    bool has_npu = false;
    bool has_gpu = false;
    bool has_cpu = false;
    uint32_t device_count = 0;
    int32_t runtime_feature_level = 0;
    std::string error;
    std::vector<NnapiDevice> devices;
};

struct HardwareProfile {
    std::string soc_name;
    std::string device_model;
    std::string target_abi;
    std::string recommended_backend;
    std::string recommended_backend_reason;
    CpuInfo cpu;
    GpuInfo gpu;
    NnapiInfo nnapi;
};

HardwareProfile probeAll();
CpuInfo probeCpu();
GpuInfo probeGpu();
NnapiInfo probeNnapi();
float readCpuTemperature();

} // namespace exynix
