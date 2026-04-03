#include "thermal_monitor.h"
#include "hardware_probe.h"
#include <android/log.h>
#include <thread>
#include <atomic>
#include <chrono>

#define LOG_TAG "ExynNix-Thermal"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace exynix {

static std::atomic<bool> g_monitoring{false};
static std::atomic<float> g_last_temp{0.0f};
static std::thread g_monitor_thread;

void startThermalMonitoring(int interval_ms) {
    g_monitoring.store(true);
    g_monitor_thread = std::thread([interval_ms]() {
        while (g_monitoring.load()) {
            float t = readCpuTemperature();
            if (t > 0) {
                g_last_temp.store(t);
                if (t > 85.0f) {
                    LOGI("THERMAL WARNING: %.1f°C — throttling likely", t);
                }
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(interval_ms));
        }
    });
}

void stopThermalMonitoring() {
    g_monitoring.store(false);
    if (g_monitor_thread.joinable()) g_monitor_thread.join();
}

float getLastTemperature() { return g_last_temp.load(); }

// Throttle detection (Exynos 990 throttles ~85°C on big cluster)
bool isThermallyThrottled() { return g_last_temp.load() > 83.0f; }

} // namespace exynix
