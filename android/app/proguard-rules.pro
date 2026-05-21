# Add project-specific ProGuard rules here.
# By default the flags in this file are appended to flags specified in proguard-android-optimize.txt.

# Keep Compose internals as needed
-keep class androidx.compose.runtime.** { *; }

# Keep entities (Room) – Room generates its own keep rules normally, this is belt-and-suspenders
-keep class com.kevin.coupy.data.entity.** { *; }
