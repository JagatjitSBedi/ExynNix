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
    -fno-finite-math-only   # Required by llama.cpp/ggml — must override -ffast-math
    -funroll-loops
    -fomit-frame-pointer
    -DANDROID
    -DARM_NEON
    -DEXYNIX_TARGET_S20_ULTRA
    -DEXYNIX_EXYNOS_990
    -Wno-format-security
    -Wno-format
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
# llama.cpp integration (via its own CMake system)
# ──────────────────────────────────────────────────────────────────────────────
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama")

if(EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(STATUS "ExynNix: llama.cpp found at ${LLAMA_DIR} — enabling real inference")

    # Exynos 990 backend flags for llama.cpp
    set(GGML_VULKAN        ON  CACHE BOOL "Enable Vulkan (Mali-G77)" FORCE)
    set(GGML_OPENMP        OFF CACHE BOOL "" FORCE)
    set(GGML_BLAS          OFF CACHE BOOL "" FORCE)
    set(GGML_CUDA          OFF CACHE BOOL "" FORCE)
    set(GGML_METAL         OFF CACHE BOOL "" FORCE)
    set(GGML_SYCL          OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TESTS  OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS  OFF CACHE BOOL "" FORCE)

    add_subdirectory(${LLAMA_DIR} llama_build)

    add_compile_definitions(HAVE_LLAMA_CPP=1)
    set(LLAMA_LIBS llama ggml)
    set(LLAMA_INCLUDES
        ${LLAMA_DIR}/include
        ${LLAMA_DIR}/ggml/include
        ${LLAMA_DIR}/src
    )
else()
    message(STATUS "ExynNix: llama.cpp NOT found — building stub mode")
    set(LLAMA_LIBS "")
    set(LLAMA_INCLUDES "")
endif()

# ──────────────────────────────────────────────────────────────────────────────
# ExynNix bridge sources
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
)

add_library(exynix_native SHARED ${BRIDGE_SRCS})

target_include_directories(exynix_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/bridge
    ${CMAKE_SOURCE_DIR}
    ${LLAMA_INCLUDES}
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
    ${LLAMA_LIBS}
)

# ──────────────────────────────────────────────────────────────────────────────
# Feature flags
# ──────────────────────────────────────────────────────────────────────────────
option(EXYNIX_BUILD_VULKAN  "Enable Mali-G77 Vulkan backend"  ON)
option(EXYNIX_BUILD_NNAPI   "Enable NNAPI delegate"           ON)
option(EXYNIX_BUILD_XNNPACK "Enable XNNPACK CPU backend"      ON)

if(EXYNIX_BUILD_VULKAN)
    target_compile_definitions(exynix_native PRIVATE EXYNIX_VULKAN=1 MALI_G77=1)
endif()
if(EXYNIX_BUILD_NNAPI)
    target_compile_definitions(exynix_native PRIVATE EXYNIX_NNAPI=1)
endif()
if(EXYNIX_BUILD_XNNPACK)
    target_compile_definitions(exynix_native PRIVATE EXYNIX_XNNPACK=1)
endif()
