#pragma once
#include <string>
#include <vector>

namespace exynix {

struct BenchmarkResult {
    std::string name;
    std::string backend;
    float score = 0;
    long time_ms = 0;
    double gflops = 0;
    double bandwidth_gbps = 0;
};

BenchmarkResult benchmarkCpuGemm(int size, int iterations);
BenchmarkResult benchmarkMemoryBandwidth(int size_mb);
BenchmarkResult benchmarkTokenizationSpeed();
std::vector<BenchmarkResult> runFullSuite();

} // namespace exynix
