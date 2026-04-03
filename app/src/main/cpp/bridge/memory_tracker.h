#pragma once

namespace exynix {

struct MemoryStats {
    long total_ram_kb = 0;
    long avail_ram_kb = 0;
    long process_rss_kb = 0;
    long process_peak_kb = 0;
    long swap_kb = 0;
};

MemoryStats getMemoryStats();

} // namespace exynix
