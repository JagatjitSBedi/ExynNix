#pragma once

namespace exynix {
void startThermalMonitoring(int interval_ms = 2000);
void stopThermalMonitoring();
float getLastTemperature();
bool isThermallyThrottled();
} // namespace exynix
