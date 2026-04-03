package com.exynix.studio.inference.jni

import android.util.Log

/**
 * JNI bridge to the native C++ inference engine.
 * Wraps the exynix_native.so shared library.
 */
object InferenceJni {

    private const val TAG = "ExynNix-JNI"

    init {
        try {
            System.loadLibrary("exynix_native")
            Log.i(TAG, "exynix_native.so loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load exynix_native.so: ${e.message}")
        }
    }

    // ── Hardware ──────────────────────────────────────────────────────────────
    external fun nativeGetHardwareProfile(): String
    external fun nativeGetCpuTemperature(): Float
    external fun nativeDetectModelFormat(path: String): String
    external fun nativeAutoSelectBackend(path: String): String

    // ── Session lifecycle ─────────────────────────────────────────────────────
    external fun nativeCreateSession(backend: String): Long
    external fun nativeLoadModel(
        handle: Long,
        path: String,
        nCtx: Int,
        nThreads: Int,
        nGpuLayers: Int,
        temperature: Float,
        topP: Float,
        maxNewTokens: Int
    ): Boolean
    external fun nativeUnloadModel(handle: Long)
    external fun nativeDestroySession(handle: Long)

    // ── Inference ─────────────────────────────────────────────────────────────
    /**
     * Blocking call — streams tokens via [callback].
     * Must be called from a background thread.
     */
    external fun nativeGenerate(
        handle: Long,
        prompt: String,
        callback: TokenCallback
    ): String   // Returns JSON stats

    external fun nativeCancel(handle: Long)

    // ── Benchmark ─────────────────────────────────────────────────────────────
    external fun nativeBenchmark(handle: Long, nTokens: Int): String

    /**
     * Callback interface called from the C++ side for each generated token.
     * Must be kept alive (no GC) for the duration of generate().
     */
    interface TokenCallback {
        fun onToken(token: String, isDone: Boolean)
        fun onStats(
            promptTps: Float, genTps: Float,
            promptTokens: Int, genTokens: Int,
            ttftMs: Long, totalMs: Long,
            backend: String, peakTemp: Float, peakMemMb: Long
        )
    }
}
