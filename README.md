# ExynNix — On-Device AI Studio for Samsung S20 Ultra (Exynos 990)

> A full-featured, open-source clone of Samsung Exynos AI Studio, **customized specifically for the Exynos 990 chipset** (Galaxy S20 Ultra). Supports CPU (XNNPACK), GPU (Mali-G77 Vulkan), NNAPI delegate, and real-time hardware monitoring.

---

## Hardware Map — Exynos 990

| Component | Spec | Status |
|-----------|------|--------|
| CPU Big   | 2× Cortex-A77 @ 2.73 GHz | ✅ XNNPACK + NEON |
| CPU Mid   | 2× Cortex-A77 @ 2.50 GHz | ✅ XNNPACK + NEON |
| CPU Little| 4× Cortex-A55 @ 2.0 GHz  | ⚠️ Avoid for inference |
| GPU       | Mali-G77 MP11 (Vulkan 1.1) | ✅ Vulkan compute |
| NPU       | Samsung 2-core (closed)   | ⚠️ Via NNAPI only |
| DSP       | Proprietary               | ⚠️ Via NNAPI only |
| RAM       | 12 GB LPDDR5              | ✅ mmap models |

---

## Features

- **5-screen UI**: Dashboard · Models · Chat · Benchmark · Settings
- **Backend router**: auto-selects best backend per model format
- **Real inference**: llama.cpp GGUF via CPU XNNPACK + Vulkan layer offload
- **Hardware probe**: reads Vulkan device caps, NNAPI devices, RAM
- **Thermal monitor**: polls CPU temperature, warns at 83°C throttle point
- **Model catalog**: 5 curated models optimized for Exynos 990
- **Streaming chat**: token-by-token output with per-message stats
- **Benchmark runner**: NEON GEMM, memory bandwidth, inference speed

---

## Build Requirements

| Tool | Version | Notes |
|------|---------|-------|
| Android Studio | Hedgehog+ | Ladybug recommended |
| Android NDK | r26c or r27 | r29 NOT on arm64 host — use r26c |
| CMake | 3.22.1+ | via SDK Manager |
| minSdk | 29 (Android 10) | S20 Ultra ships with Android 10 |
| targetSdk | 35 | |
| ABI | arm64-v8a | Only ABI compiled |

---

## Step 1: Add llama.cpp (Required for Real Inference)

```bash
cd app/src/main/cpp
git clone https://github.com/ggerganov/llama.cpp llama
cd llama
git checkout b4435  # stable tag — or use latest main
```

The CMakeLists.txt detects llama.cpp with `__has_include("../llama/llama.h")`.
Without it, the app builds and runs with **stub mode** — UI/JNI/hardware probe fully work.

---

## Step 2: Configure local.properties

```properties
sdk.dir=/path/to/Android/Sdk
ndk.dir=/path/to/Android/Sdk/ndk/26.3.11579264
```

On a Linux host:
```bash
sdk.dir=/home/user/Android/Sdk
ndk.dir=/home/user/Android/Sdk/ndk/26.3.11579264
```

---

## Step 3: Build

### Android Studio (recommended)
1. Open `/ExynNix` in Android Studio
2. Sync Gradle → `File > Sync Project with Gradle Files`
3. Select device: `Samsung Galaxy S20 Ultra` (or connected ADB device)
4. `Run > Run 'app'` (or Shift+F10)

### Command line (cross-compile from Linux x86_64)
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK=$ANDROID_HOME/ndk/26.3.11579264
export PATH=$ANDROID_HOME/platform-tools:$PATH

cd ExynNix
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`

### Deploy via ADB
```bash
# Install
adb install app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# Push model files to device storage
adb push ~/models/Llama-3.2-3B-Instruct-Q4_K_M.gguf /sdcard/Download/
# Then in-app: use the Models tab to point to /sdcard/Download/

# View native logs
adb logcat -s ExynNix-JNI ExynNix-HWProbe ExynNix-Vulkan ExynNix-CPU ExynNix-NNAPI
```

---

## Step 4: Place Models

Models go to either:
- `context.filesDir/models/` (downloaded in-app)
- `/sdcard/Download/` (manually pushed via ADB)

The app's Model Manager downloads directly from HuggingFace. Tap **Download** on any model card.

### Recommended for S20 Ultra Exynos 990

| Model | Size | Backend | Expected t/s |
|-------|------|---------|-------------|
| Llama 3.2 3B Q4_K_M | 1.9 GB | GPU Vulkan | 8-18 |
| Gemma 2 2B Q4_K_M | 1.6 GB | GPU Vulkan | 10-20 |
| Phi-3 Mini Q4_K_M | 2.2 GB | GPU Vulkan | 7-15 |
| Qwen 2.5 1.5B Q8_0 | 1.6 GB | CPU XNNPACK | 20-35 |
| Llama 3 8B Q4_K_M | 4.7 GB | GPU Vulkan | 4-10 |

---

## Optimal CMake Flags for Exynos 990

```cmake
-march=armv8.2-a+dotprod+fp16    # Cortex-A77 exact ISA
-mtune=cortex-a77
-O3 -ffast-math -funroll-loops
```

For llama.cpp (add to LLAMA_ADDITIONAL_C_FLAGS):
```
-DGGML_USE_VULKAN=1          # Mali-G77 Vulkan backend
-DGGML_VULKAN_MEMORY_DEBUG=0
-DGGML_USE_CPU_AARCH64=1     # ARM64 optimized kernels
```

---

## Backend Decision Matrix

| Model Format | Best Backend | Fallback |
|-------------|-------------|---------|
| `.gguf` 3B  | GPU_VULKAN (full layers) | CPU_XNNPACK |
| `.gguf` 7B  | GPU_VULKAN (20-24 layers) | CPU_XNNPACK |
| `.tflite`   | NNAPI_GPU / NNAPI_NPU | CPU |
| `.onnx`     | NNAPI_GPU | CPU |
| `.pte`      | GPU_VULKAN | CPU_XNNPACK |

---

## Project Structure

```
ExynNix/
├── app/
│   ├── src/main/
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt          # Build with Vulkan/NNAPI flags
│   │   │   └── bridge/
│   │   │       ├── exynix_jni.cpp      # JNI bridge (main entry)
│   │   │       ├── hardware_probe.cpp  # Vulkan/NNAPI/CPU detection
│   │   │       ├── backend_router.cpp  # Auto backend selection
│   │   │       ├── cpu_backend.cpp     # llama.cpp CPU (XNNPACK)
│   │   │       ├── vulkan_backend.cpp  # llama.cpp Vulkan (Mali-G77)
│   │   │       ├── nnapi_backend.cpp   # Android NNAPI delegate
│   │   │       ├── benchmark_runner.cpp# NEON GEMM + mem bandwidth
│   │   │       ├── thermal_monitor.cpp # /sys/class/thermal polling
│   │   │       └── tokenizer.cpp       # Prompt template formatting
│   │   └── java/com/exynix/studio/
│   │       ├── MainActivity.kt         # Nav host + bottom nav
│   │       ├── ExynNixApp.kt           # Hilt application
│   │       ├── data/models/Models.kt   # All data classes + catalog
│   │       ├── data/repository/        # InferenceRepository (JNI glue)
│   │       ├── viewmodel/MainViewModel.kt
│   │       └── ui/
│   │           ├── theme/Theme.kt      # Dark theme + color tokens
│   │           ├── components/Components.kt
│   │           └── screens/
│   │               ├── DashboardScreen.kt
│   │               ├── ModelManagerScreen.kt
│   │               ├── ChatScreen.kt
│   │               ├── BenchmarkScreen.kt
│   │               └── SettingsScreen.kt
├── app/build.gradle.kts                # NDK flags, ABI filter, Vulkan
├── gradle/libs.versions.toml           # All dependency versions
└── README.md
```

---

## Thermal Management Notes

The Exynos 990 throttles aggressively:
- Big cluster throttle: ~83°C (2.73 → 2.0 GHz)
- Full throttle: ~90°C (CPU stuck at 1 GHz)
- GPU throttle: ~80°C (Mali-G77 drops clocks)

ExynNix monitors `/sys/class/thermal/thermal_zone*/temp` every 3 seconds.
The top-right indicator turns orange at 70°C and red at 83°C.

**Mitigation**: Use Qwen 2.5 1.5B or Gemma 2 2B for sustained inference — smaller models = less heat.

---

## NPU Note

The Exynos 990 NPU is **not publicly exposed** via ENN SDK (that's Exynos 2400+/2500+ only).
ExynNix routes NPU-bound work through NNAPI, which may or may not delegate to NPU depending on:
- Your ROM (OneUI vs AOSP)
- Driver version
- Operator support

To check if NPU is active:
```bash
adb logcat | grep "ExynNix-NNAPI"
# Look for: "NNAPI device[0]: ACCELERATOR"
```

---

## License

Apache 2.0 — free to use, modify, and deploy commercially.
