# WebRTC calls into Java through JNI by name, so none of it may be renamed or removed.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
# OkHttp's optional platform integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# WebRTC's native code starts by calling org.jni_zero.JniInit and uses JniUtil through JNI.
# Nothing in Java calls them, so without this R8 removes them and the native library aborts
# the moment the streaming engine starts.
-keep class org.jni_zero.** { *; }
-dontwarn org.jni_zero.**
