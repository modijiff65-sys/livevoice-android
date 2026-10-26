# RootEncoder / MediaCodec / JNI entry points are reached reflectively.
-keep class com.pedro.** { *; }
-dontwarn com.pedro.**
-keep class androidx.security.crypto.** { *; }
-dontwarn org.jetbrains.annotations.**
