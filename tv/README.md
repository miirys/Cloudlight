# Cloudlight TV

A GeForce NOW client for Android TV, written from scratch. It streams up to 1440p at 120 fps,
works with controllers, keyboards and mice, and picks up sessions started on other devices.

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

Release builds are signed with `app/release.jks`, which is committed on purpose so every
build can update the last one. Set `CLOUDLIGHT_KEYSTORE` and `CLOUDLIGHT_KEYSTORE_PASSWORD`
(optionally `CLOUDLIGHT_KEY_ALIAS` and `CLOUDLIGHT_KEY_PASSWORD`) to sign with another key.

A push whose commit message contains `[release]` also publishes the APK as a GitHub release,
using `RELEASE_NOTES.md` as the notes.

## Layout

| Path | What it is |
| --- | --- |
| `app/` | The client: Compose interface, GeForce NOW sign-in and session APIs, WebRTC streaming. |
| `buildSrc/` | A build-time patch to WebRTC's video texture path that avoids dropped frames at 120 fps. |
| `probe/` | A hardware probe used during development: decoders, display modes and input devices. |

Inside the app:

- `stream/` holds the streaming engine: signaling, SDP, input encoding and stats.
- `input/UsbPadDriver.kt` reads USB controllers directly (Xbox 360, Xbox One and plain HID
  pads) for dongles the TV's kernel doesn't recognise.
- `input/PadHub.kt` merges every controller into menu actions with gently accelerating repeat.
- `ui/motion/Motion.kt` has the spring animations used everywhere.
