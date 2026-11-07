# WebRTC calls into Java through JNI by name, so none of it may be renamed or removed.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
# OkHttp's optional platform integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
