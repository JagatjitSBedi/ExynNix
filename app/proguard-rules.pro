# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}
# Keep ExynNix JNI bridge
-keep class com.exynix.studio.inference.jni.** { *; }
-keep class com.exynix.studio.data.models.** { *; }
# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
# Keep enums
-keepclassmembers enum * { *; }
