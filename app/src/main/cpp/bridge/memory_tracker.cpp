#include "memory_tracker.h"
#include <android/log.h>
#include <fstream>
#include <string>
#include <sstream>

#define LOG_TAG "ExynNix-Mem"

namespace exynix {

MemoryStats getMemoryStats() {
    MemoryStats stats{};

    // Read /proc/self/status for process memory
    std::ifstream status("/proc/self/status");
    std::string line;
    while (std::getline(status, line)) {
        if (line.rfind("VmRSS:", 0) == 0) {
            sscanf(line.c_str() + 6, "%ld", &stats.process_rss_kb);
        } else if (line.rfind("VmPeak:", 0) == 0) {
            sscanf(line.c_str() + 7, "%ld", &stats.process_peak_kb);
        } else if (line.rfind("VmSwap:", 0) == 0) {
            sscanf(line.c_str() + 7, "%ld", &stats.swap_kb);
        }
    }

    // System memory from /proc/meminfo
    std::ifstream meminfo("/proc/meminfo");
    while (std::getline(meminfo, line)) {
        long val;
        if (sscanf(line.c_str(), "MemTotal: %ld kB", &val) == 1) stats.total_ram_kb = val;
        else if (sscanf(line.c_str(), "MemAvailable: %ld kB", &val) == 1) stats.avail_ram_kb = val;
    }

    return stats;
}

} // namespace exynix
