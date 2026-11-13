# Cloudlight TV

A GeForce NOW client for Android TV, written from scratch. It streams up to 1440p at 120 fps
(and, experimentally, 4K at 60 fps), works with controllers, keyboards and mice, and picks up
sessions started on other devices.

## Installing

Download the APK from the latest release and install it on the TV, for example with
"Send files to TV" or `adb install`. Android TV 9 or newer is required.

## Building

CI builds the APKs on every push that touches `tv/` (workflow "Cloudlight TV (Android)") and
uploads them as a run artifact.

Locally you need JDK 17 and the Android SDK (platform 35):

```sh
./gradlew :app:assembleRelease
```

The 4K mode needs the native streaming library, which Gradle does not build. Build it first
(Rust with the `aarch64-linux-android` and `armv7-linux-androideabi` targets, the Android NDK
and CMake), or the app is built without it and the 4K switch falls back to the normal stream:

```sh
python3 scripts/build_nvst.py "$ANDROID_SDK_ROOT/ndk/27.2.12479018" app/src/main/jniLibs
```

Release builds are signed with `app/release.jks`, which is committed on purpose so every
build can update the last one. Set `CLOUDLIGHT_KEYSTORE` and `CLOUDLIGHT_KEYSTORE_PASSWORD`
(optionally `CLOUDLIGHT_KEY_ALIAS` and `CLOUDLIGHT_KEY_PASSWORD`) to sign with another key.

A push whose commit message contains `[release]` also publishes the APK as a GitHub release,
using `RELEASE_NOTES.md` as the notes.

## Layout

| Path | What it is |
| --- | --- |
| `app/` | The client: Compose interface, GeForce NOW sign-in and session APIs, WebRTC streaming. |
| `buildSrc/` | A build-time patch to WebRTC's hardware decoder: it waits for the renderer instead of dropping frames at 120 fps, and tells the decoder the stream's frame rate. Calls into `stream/VideoHooks.kt`. |
| `nvst/` | NVIDIA's native streaming protocol (NVST) as a Rust library, taken from OpenNOW (MIT). The 4K mode runs on it; `stream/NativeTransport.kt` is its Kotlin side. See `nvst/UPSTREAM.md`. |
| `probe/` | A hardware probe used during development: decoders, display modes and input devices. |

Inside the app:

- `stream/` holds the streaming engine: signaling, SDP, input encoding and stats.
- `input/UsbPadDriver.kt` reads USB controllers directly (Xbox 360, Xbox One and plain HID
  pads) for dongles the TV's kernel doesn't recognise.
- `input/PadHub.kt` merges every controller into menu actions with gently accelerating repeat.
- `ui/motion/Motion.kt` has the spring animations used everywhere.
