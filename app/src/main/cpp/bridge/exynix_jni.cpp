/**
 * ExynNix — JNI Bridge
 * Exposes C++ inference engine to Kotlin via JNI.
 * Registered as: com.exynix.studio.inference.jni.InferenceJni
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <map>
#include <mutex>
#include <sstream>

#include "hardware_probe.h"
#include "backend_router.h"
#include "inference_session.h"

#define LOG_TAG "ExynNix-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ────────────────────────────────────────────────────────────────────────────
// Session registry (handles sessions across multiple JNI calls)
// ────────────────────────────────────────────────────────────────────────────
static std::map<jlong, std::unique_ptr<exynix::InferenceSession>> g_sessions;
static std::mutex g_mutex;
static jlong g_next_handle = 1;

extern "C" {

// ────────────────────────────────────────────────────────────────────────────
// Hardware probing
// ────────────────────────────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeGetHardwareProfile(JNIEnv* env, jclass) {
    auto profile = exynix::probeAll();
    std::ostringstream json;
    json << "{"
         << "\"soc\":\"" << profile.soc_name << "\","
         << "\"device\":\"" << profile.device_model << "\","
         << "\"recommended_backend\":\"" << profile.recommended_backend << "\","
         << "\"recommended_reason\":\"" << profile.recommended_backend_reason << "\","
         << "\"cpu\":{"
         <<   "\"arch\":\"" << profile.cpu.arch << "\","
         <<   "\"big_cores\":" << profile.cpu.big_cores << ","
         <<   "\"mid_cores\":" << profile.cpu.mid_cores << ","
         <<   "\"little_cores\":" << profile.cpu.little_cores << ","
         <<   "\"has_dotprod\":" << (profile.cpu.has_dotprod ? "true" : "false") << ","
         <<   "\"has_fp16\":" << (profile.cpu.has_fp16 ? "true" : "false") << ","
         <<   "\"total_ram_mb\":" << profile.cpu.total_ram_mb << ","
         <<   "\"avail_ram_mb\":" << profile.cpu.avail_ram_mb
         << "},"
         << "\"gpu\":{"
         <<   "\"available\":" << (profile.gpu.available ? "true" : "false") << ","
         <<   "\"device_name\":\"" << profile.gpu.device_name << "\","
         <<   "\"is_mali\":" << (profile.gpu.is_mali ? "true" : "false") << ","
         <<   "\"total_vram_mb\":" << profile.gpu.total_vram_mb << ","
         <<   "\"supports_fp16\":" << (profile.gpu.supports_fp16_compute ? "true" : "false")
         << "},"
         << "\"nnapi\":{"
         <<   "\"available\":" << (profile.nnapi.available ? "true" : "false") << ","
         <<   "\"device_count\":" << profile.nnapi.device_count << ","
         <<   "\"has_npu\":" << (profile.nnapi.has_npu ? "true" : "false") << ","
         <<   "\"has_gpu\":" << (profile.nnapi.has_gpu ? "true" : "false") << ","
         <<   "\"has_cpu\":" << (profile.nnapi.has_cpu ? "true" : "false")
         << "}"
         << "}";
    return env->NewStringUTF(json.str().c_str());
}

JNIEXPORT jfloat JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeGetCpuTemperature(JNIEnv*, jclass) {
    return exynix::readCpuTemperature();
}

JNIEXPORT jstring JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeDetectModelFormat(JNIEnv* env, jclass,
    jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    exynix::ModelFormat fmt = exynix::detectFormat(path);
    env->ReleaseStringUTFChars(jpath, path);
    switch (fmt) {
        case exynix::ModelFormat::GGUF:   return env->NewStringUTF("GGUF");
        case exynix::ModelFormat::PTE:    return env->NewStringUTF("PTE");
        case exynix::ModelFormat::ONNX:   return env->NewStringUTF("ONNX");
        case exynix::ModelFormat::TFLITE: return env->NewStringUTF("TFLITE");
        default:                          return env->NewStringUTF("UNKNOWN");
    }
}

JNIEXPORT jstring JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeAutoSelectBackend(JNIEnv* env, jclass,
    jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    exynix::Backend b = exynix::autoSelectBackend(path);
    env->ReleaseStringUTFChars(jpath, path);
    switch (b) {
        case exynix::Backend::GPU_VULKAN:  return env->NewStringUTF("GPU_VULKAN");
        case exynix::Backend::NNAPI_GPU:   return env->NewStringUTF("NNAPI_GPU");
        case exynix::Backend::NNAPI_NPU:   return env->NewStringUTF("NNAPI_NPU");
        default:                           return env->NewStringUTF("CPU_XNNPACK");
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Session lifecycle
// ────────────────────────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeCreateSession(JNIEnv* env, jclass,
    jstring jBackend) {
    const char* backend_str = env->GetStringUTFChars(jBackend, nullptr);
    exynix::Backend backend = exynix::Backend::AUTO;
    if (std::string(backend_str) == "GPU_VULKAN")  backend = exynix::Backend::GPU_VULKAN;
    else if (std::string(backend_str) == "NNAPI_GPU") backend = exynix::Backend::NNAPI_GPU;
    else if (std::string(backend_str) == "NNAPI_NPU") backend = exynix::Backend::NNAPI_NPU;
    else if (std::string(backend_str) == "CPU_XNNPACK") backend = exynix::Backend::CPU_XNNPACK;
    env->ReleaseStringUTFChars(jBackend, backend_str);

    auto session = exynix::createSession(backend);
    std::lock_guard<std::mutex> lock(g_mutex);
    jlong handle = g_next_handle++;
    g_sessions[handle] = std::move(session);
    LOGI("Created session handle=%lld backend=%s", (long long)handle, backend_str);
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeLoadModel(JNIEnv* env, jclass,
    jlong handle, jstring jPath, jint nCtx, jint nThreads, jint nGpuLayers,
    jfloat temperature, jfloat topP, jint maxNewTokens) {

    exynix::InferenceConfig config;
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    config.model_path = path;
    config.format = exynix::detectFormat(path);
    env->ReleaseStringUTFChars(jPath, path);

    config.n_ctx = nCtx;
    config.n_threads = nThreads;
    config.n_gpu_layers = nGpuLayers;
    config.temperature = temperature;
    config.top_p = topP;
    config.max_new_tokens = maxNewTokens;
    config.use_mmap = true;

    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        LOGE("Session not found: %lld", (long long)handle);
        return JNI_FALSE;
    }
    return it->second->load(config) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeUnloadModel(JNIEnv*, jclass, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_sessions.find(handle);
    if (it != g_sessions.end()) it->second->unload();
}

JNIEXPORT void JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeDestroySession(JNIEnv*, jclass, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_sessions.erase(handle);
    LOGI("Destroyed session handle=%lld", (long long)handle);
}

// ────────────────────────────────────────────────────────────────────────────
// Inference (streaming, calls back into Kotlin on each token)
// ────────────────────────────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeGenerate(JNIEnv* env, jclass,
    jlong handle, jstring jPrompt, jobject callback) {

    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt_str(prompt);
    env->ReleaseStringUTFChars(jPrompt, prompt);

    exynix::InferenceStats stats{};

    // Get callback class + method
    jclass cb_class = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;Z)V");
    jmethodID onStats = env->GetMethodID(cb_class, "onStats",
        "(FFIIJJLjava/lang/String;FJ)V");

    JavaVM* jvm = nullptr;
    env->GetJavaVM(&jvm);
    jobject cb_global = env->NewGlobalRef(callback);

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_sessions.find(handle);
        if (it == g_sessions.end()) {
            env->DeleteGlobalRef(cb_global);
            return env->NewStringUTF("{\"error\":\"Session not found\"}");
        }

        it->second->generate(prompt_str,
            [&](const std::string& token, bool done) {
                JNIEnv* tenv = nullptr;
                bool attached = false;
                if (jvm->GetEnv((void**)&tenv, JNI_VERSION_1_6) == JNI_EDETACHED) {
                    jvm->AttachCurrentThread(&tenv, nullptr);
                    attached = true;
                }
                jstring jtok = tenv->NewStringUTF(token.c_str());
                tenv->CallVoidMethod(cb_global, onToken, jtok, done ? JNI_TRUE : JNI_FALSE);
                tenv->DeleteLocalRef(jtok);
                if (attached) jvm->DetachCurrentThread();
            },
            stats
        );
    }

    // Return stats as JSON
    std::string backend_name;
    switch (stats.backend_used) {
        case exynix::Backend::GPU_VULKAN:  backend_name = "GPU_VULKAN"; break;
        case exynix::Backend::NNAPI_GPU:   backend_name = "NNAPI_GPU"; break;
        case exynix::Backend::NNAPI_NPU:   backend_name = "NNAPI_NPU"; break;
        default:                           backend_name = "CPU_XNNPACK"; break;
    }

    std::ostringstream json;
    json << "{"
         << "\"prompt_tps\":" << stats.prompt_tokens_per_sec << ","
         << "\"gen_tps\":" << stats.gen_tokens_per_sec << ","
         << "\"prompt_tokens\":" << stats.prompt_tokens << ","
         << "\"gen_tokens\":" << stats.generated_tokens << ","
         << "\"ttft_ms\":" << stats.time_to_first_token_ms << ","
         << "\"total_ms\":" << stats.total_time_ms << ","
         << "\"backend\":\"" << backend_name << "\","
         << "\"peak_temp\":" << stats.peak_cpu_temp
         << "}";

    env->DeleteGlobalRef(cb_global);
    return env->NewStringUTF(json.str().c_str());
}

JNIEXPORT void JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeCancel(JNIEnv*, jclass, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_sessions.find(handle);
    if (it != g_sessions.end()) it->second->cancel();
}

// ────────────────────────────────────────────────────────────────────────────
// Benchmark
// ────────────────────────────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_exynix_studio_inference_jni_InferenceJni_nativeBenchmark(JNIEnv* env, jclass,
    jlong handle, jint n_tokens) {

    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        return env->NewStringUTF("{\"error\":\"Session not found\"}");
    }

    // Run standard benchmark prompt
    std::string bench_prompt = "The quick brown fox jumps over the lazy dog. "
                               "Repeat this sentence in different creative ways: ";
    exynix::InferenceStats stats{};
    exynix::InferenceConfig bench_config;
    bench_config.max_new_tokens = n_tokens;
    bench_config.temperature = 1.0f;

    bool done_flag = false;
    it->second->generate(bench_prompt,
        [&](const std::string&, bool done) { if (done) done_flag = true; },
        stats
    );

    std::ostringstream json;
    json << "{"
         << "\"gen_tps\":" << stats.gen_tokens_per_sec << ","
         << "\"prompt_tps\":" << stats.prompt_tokens_per_sec << ","
         << "\"ttft_ms\":" << stats.time_to_first_token_ms << ","
         << "\"total_ms\":" << stats.total_time_ms << ","
         << "\"tokens_generated\":" << stats.generated_tokens
         << "}";
    return env->NewStringUTF(json.str().c_str());
}

} // extern "C"
