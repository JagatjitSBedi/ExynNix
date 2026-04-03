/**
 * ExynNix — Benchmark Runner
 * Runs standardized hardware benchmarks targeting Exynos 990 components.
 */

#include "benchmark_runner.h"
#include <android/log.h>
#include <chrono>
#include <cmath>
#include <vector>
#include <numeric>
#include <arm_neon.h>   // NEON intrinsics

#define LOG_TAG "ExynNix-Bench"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace exynix {

// ────────────────────────────────────────────────────────────────────────────
// CPU NEON FP32 GEMM benchmark (measures Cortex-A77 throughput)
// ────────────────────────────────────────────────────────────────────────────
BenchmarkResult benchmarkCpuGemm(int size, int iterations) {
    BenchmarkResult result{};
    result.name = "CPU NEON GEMM";
    result.backend = "CPU (Cortex-A77)";

    std::vector<float> A(size * size, 1.0f);
    std::vector<float> B(size * size, 1.0f);
    std::vector<float> C(size * size, 0.0f);

    auto t_start = std::chrono::steady_clock::now();

    for (int iter = 0; iter < iterations; iter++) {
        // Simple NEON-vectorized matmul for benchmark
        for (int i = 0; i < size; i++) {
            for (int k = 0; k < size; k++) {
                float a_val = A[i * size + k];
                float32x4_t va = vdupq_n_f32(a_val);
                int j = 0;
                for (; j + 4 <= size; j += 4) {
                    float32x4_t vb = vld1q_f32(&B[k * size + j]);
                    float32x4_t vc = vld1q_f32(&C[i * size + j]);
                    vc = vmlaq_f32(vc, va, vb);
                    vst1q_f32(&C[i * size + j], vc);
                }
                for (; j < size; j++) {
                    C[i * size + j] += a_val * B[k * size + j];
                }
            }
        }
    }

    auto t_end = std::chrono::steady_clock::now();
    long ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();

    // FLOPS = 2 * N^3 * iterations
    double flops = 2.0 * size * size * size * iterations;
    result.gflops = (ms > 0) ? (flops / ms / 1e6) : 0;  // GFLOPS
    result.time_ms = ms;
    result.score = (float)result.gflops;

    LOGI("CPU GEMM %dx%d x%d: %.2f GFLOPS in %ldms", size, size, iterations, result.gflops, ms);
    return result;
}

// ────────────────────────────────────────────────────────────────────────────
// Memory bandwidth benchmark
// ────────────────────────────────────────────────────────────────────────────
BenchmarkResult benchmarkMemoryBandwidth(int size_mb) {
    BenchmarkResult result{};
    result.name = "Memory Bandwidth";
    result.backend = "CPU (LPDDR5)";

    size_t n = (size_t)size_mb * 1024 * 1024 / sizeof(float);
    std::vector<float> src(n, 1.0f);
    std::vector<float> dst(n, 0.0f);

    auto t_start = std::chrono::steady_clock::now();

    // Sequential read-write
    for (size_t i = 0; i < n; i += 4) {
        float32x4_t v = vld1q_f32(&src[i]);
        vst1q_f32(&dst[i], v);
    }

    auto t_end = std::chrono::steady_clock::now();
    long ms = std::chrono::duration_cast<std::chrono::milliseconds>(t_end - t_start).count();

    double bytes = (double)n * sizeof(float) * 2;  // read + write
    result.bandwidth_gbps = (ms > 0) ? (bytes / ms / 1e6) : 0;
    result.time_ms = ms;
    result.score = (float)result.bandwidth_gbps;

    LOGI("Memory BW %dMB: %.2f GB/s in %ldms", size_mb, result.bandwidth_gbps, ms);
    return result;
}

// ────────────────────────────────────────────────────────────────────────────
// Tokenization benchmark
// ────────────────────────────────────────────────────────────────────────────
BenchmarkResult benchmarkTokenizationSpeed() {
    BenchmarkResult result{};
    result.name = "Tokenizer Speed";
    result.backend = "CPU";
    // Placeholder — real benchmark requires llama.cpp tokenizer
    result.score = 0;
    result.time_ms = 0;
    return result;
}

// ────────────────────────────────────────────────────────────────────────────
// Suite runner
// ────────────────────────────────────────────────────────────────────────────
std::vector<BenchmarkResult> runFullSuite() {
    std::vector<BenchmarkResult> results;
    results.push_back(benchmarkCpuGemm(256, 5));
    results.push_back(benchmarkCpuGemm(512, 2));
    results.push_back(benchmarkMemoryBandwidth(128));
    results.push_back(benchmarkTokenizationSpeed());
    return results;
}

} // namespace exynix
