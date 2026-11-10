# Laterbox Proguard / R8 Optimization & Obfuscation Rules

# Room SQLite Database & Entities
-keep class * extends androidx.room.RoomDatabase
-keep class com.laterbox.app.data.local.entity.** { *; }
-keep class com.laterbox.app.data.local.dao.** { *; }
-dontwarn androidx.room.paging.**

# WorkManager Workers
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.laterbox.app.data.metadata.MetadataSyncWorker { *; }

# Jsoup HTML Parser
-keep public class org.jsoup.** { public *; }

# Coil Image Loader
-dontwarn coil.**
-keep class coil.** { *; }

# Domain & Backup Data Models
-keep class com.laterbox.app.domain.model.** { *; }
-keep class com.laterbox.app.data.backup.** { *; }

# Jetpack Compose
-dontwarn androidx.compose.**
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# youtubedl-android (yt-dlp, FFmpeg, QuickJS)
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Apache Commons Compress & IO (ExtraFieldUtils uses Class.newInstance() reflection on ZipExtraField subclasses)
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**

# Jackson JSON Models & Databind (used by YoutubeDL.getInfo, VideoInfo, and YoutubeDLUpdater)
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

