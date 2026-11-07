# Laterbox Proguard / R8 Rules

# Jsoup
-keep public class org.jsoup.** { public *; }

# Coil
-dontwarn coil.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
