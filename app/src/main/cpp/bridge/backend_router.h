#pragma once
#include "inference_session.h"
#include "hardware_probe.h"
#include <string>

namespace exynix {

const HardwareProfile& getCachedProfile();
ModelFormat detectFormat(const std::string& path);
std::string recommendQuantization(long model_size_mb, long avail_ram_mb);

} // namespace exynix
