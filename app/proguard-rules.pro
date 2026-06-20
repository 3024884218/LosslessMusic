# Gradle 默认规则
-keep class com.lossless.music.** { *; }

# Media3
-keep class androidx.media3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
