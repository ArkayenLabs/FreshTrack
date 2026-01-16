# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ==========================================
# Room Database Rules
# ==========================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Room DAOs
-keep interface * extends androidx.room.* { *; }

# ==========================================
# Koin Dependency Injection Rules
# ==========================================
-keepnames class * extends org.koin.core.module.Module
-keep class org.koin.** { *; }
-keep class * extends org.koin.core.component.KoinComponent { *; }

# ==========================================
# Kotlin Coroutines Rules
# ==========================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ==========================================
# Jetpack Compose Rules
# ==========================================
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ==========================================
# ML Kit Barcode Scanning Rules
# ==========================================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ==========================================
# CameraX Rules
# ==========================================
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ==========================================
# DataStore Preferences Rules
# ==========================================
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ==========================================
# Coil Image Loading Rules
# ==========================================
-dontwarn coil.**
-keep class coil.** { *; }

# ==========================================
# General Android Rules
# ==========================================
# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep Android Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==========================================
# FreshTrack App-Specific Rules
# ==========================================
# Keep domain models
-keep class com.example.freshtrack.domain.model.** { *; }

# Keep data entities
-keep class com.example.freshtrack.data.local.entities.** { *; }

# Keep DAOs
-keep interface com.example.freshtrack.data.local.dao.** { *; }