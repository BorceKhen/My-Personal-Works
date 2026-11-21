# Keep WebRTC native JNI callbacks and Ktor CIO classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class io.ktor.** { *; }
