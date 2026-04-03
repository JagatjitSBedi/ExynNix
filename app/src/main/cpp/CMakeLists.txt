cmake_minimum_required(VERSION 3.22.1)
project(exynix_native)

# ──────────────────────────────────────────────────────────────────────────────
# Compiler flags tuned for Exynos 990 / Cortex-A77 + Mali-G77 MP11
# ──────────────────────────────────────────────────────────────────────────────
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_compile_options(
    -O3
    -march=armv8.2-a+dotprod+fp16
    -mtune=cortex-a77
    -ffast-math
    -funroll-loops
    -fomit-frame-pointer
    -DANDROID
    -DARM_NEON
    -DEXYNIX_TARGET_S20_ULTRA
    -DEXYNIX_EXYNOS_990
)

# ──────────────────────────────────────────────────────────────────────────────
# Android system libraries
# ──────────────────────────────────────────────────────────────────────────────
find_library(log-lib          log)
find_library(android-lib      android)
find_library(jnigraphics-lib  jnigraphics)
find_library(nnapi-lib        neuralnetworks)
find_library(vulkan-lib       vulkan)
find_library(egl-lib          EGL)
find_library(gles3-lib        GLESv3)

# ──────────────────────────────────────────────────────────────────────────────
# Sources
# ──────────────────────────────────────────────────────────────────────────────
set(BRIDGE_SRCS
    bridge/exynix_jni.cpp
    bridge/hardware_probe.cpp
    bridge/backend_router.cpp
    bridge/vulkan_backend.cpp
    bridge/nnapi_backend.cpp
    bridge/cpu_backend.cpp
    bridge/tokenizer.cpp
    bridge/benchmark_runner.cpp
    bridge/thermal_monitor.cpp
    bridge/memory_tracker.cpp
    # inference_session.h is a pure interface header — no .cpp needed
)

add_library(exynix_native SHARED ${BRIDGE_SRCS})

target_include_directories(exynix_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/bridge
    ${CMAKE_SOURCE_DIR}
    # NDK NNAPI headers (location varies by NDK version)
    ${ANDROID_NDK}/toolchains/llvm/prebuilt/${ANDROID_HOST_TAG}/sysroot/usr/include
    ${ANDROID_SYSROOT}/usr/include
)

target_link_libraries(exynix_native
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
    ${nnapi-lib}
    ${vulkan-lib}
    ${egl-lib}
    ${gles3-lib}
)

# ──────────────────────────────────────────────────────────────────────────────
# Compile definitions by capability
# ──────────────────────────────────────────────────────────────────────────────
option(EXYNIX_BUILD_VULKAN  "Enable Mali-G77 Vulkan backend"  ON)
option(EXYNIX_BUILD_NNAPI   "Enable NNAPI (CPU/GPU/NPU delegate)" ON)
option(EXYNIX_BUILD_XNNPACK "Enable XNNPACK CPU backend"     ON)

if(EXYNIX_BUILD_VULKAN)
    target_compile_definitions(exynix_native PRIVATE EXYNIX_VULKAN=1)
    target_compile_definitions(exynix_native PRIVATE MALI_G77=1)
endif()

if(EXYNIX_BUILD_NNAPI)
    target_compile_definitions(exynix_native PRIVATE EXYNIX_NNAPI=1)
endif()

if(EXYNIX_BUILD_XNNPACK)
    target_compile_definitions(exynix_native PRIVATE EXYNIX_XNNPACK=1)
endif()
