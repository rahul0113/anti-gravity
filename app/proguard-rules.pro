# ProGuard rules for Anti-Gravity Vibe Coder
# Required when isMinifyEnabled = true (release builds)

# ---- OkHttp3 ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---- JSch (SSH) ----
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# ---- Kotlinx Serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static ** Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Kotlin ----
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# ---- Compose / Android ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---- Security Crypto (EncryptedSharedPreferences) ----
-keep class androidx.security.crypto.** { *; }

# ---- Our own models (needed for serialization) ----
-keep class com.antigravity.vibecoder.data.ZenMessage { *; }
-keep class com.antigravity.vibecoder.data.ZenRequest { *; }
-keep class com.antigravity.vibecoder.data.ZenResponse { *; }
-keep class com.antigravity.vibecoder.data.ZenChoice { *; }
-keep class com.antigravity.vibecoder.model.** { *; }
