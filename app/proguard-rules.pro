# ProGuard rules for Appy

# Keep Zip4j classes
-keep class net.lingala.zip4j.** { *; }

# Keep APK processing classes
-keep class com.appy.processor.** { *; }

# Keep Compose classes
-dontwarn androidx.compose.**
