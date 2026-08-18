# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep WorkManager workers (instantiated reflectively).
-keep class com.knicventures.mediakit.work.** { *; }
