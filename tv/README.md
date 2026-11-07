# Cloudlight TV

A cloud gaming client written from scratch for the Zidoo Z30 Pro (Android TV box). It shares
no code with the desktop app or OpenNOW. The architecture is described in the
[proposal](https://claude.ai/artifact/4eUZWiyaP1cEYMy83oazus).

## Modules

| Module | What it is |
| --- | --- |
| `probe/` | Phase 0 hardware probe: decoders and display modes, a decode stress test at 1080p120 and 4K60, every input and USB device, live controller and mouse events, pointer capture, and a raw USB gamepad reader for dongles the kernel does not recognise. |

## Building

CI builds the APKs on every push that touches `tv/` (workflow "Cloudlight TV (Android)"),
and uploads them as a run artifact.

Locally you need JDK 17, the Android SDK (platform 35) and ffmpeg:

```sh
./probe/make-clips.sh            # decoder test clips, about 50 MB, not committed
./gradlew :probe:assembleRelease
```

## Using the probe

1. Install the APK on the Zidoo and open **Cloudlight Probe**.
2. Plug in the 8BitDo 2.4 GHz dongle and a mouse, then press **Refresh report**.
3. Press **Decode test** and wait about 30 seconds.
4. Press **Try 1080p 120 Hz** and **Try 4K 60 Hz** and check the log says the display switched.
5. Press **Mouse capture**, move the mouse, then press Esc or B.
6. Press **Read USB pad**, accept the permission dialog, move the sticks, then press it again.
7. On a PC on the same network, open the address shown at the top and copy the whole page.

## App (`:app`)

The Cloudlight client itself. Phase 1 contains the shell: the design system, spring-based
motion (`ui/motion/Motion.kt`), the focus system, and the controller layer.

- `input/UsbPadDriver.kt` is Cloudlight's own USB controller driver. It claims Xbox 360
  (XInput), Xbox One (GIP) and plain HID pads directly, because the Zidoo's kernel ignores
  8BitDo's 2.4 GHz dongle.
- `input/PadHub.kt` merges every controller (USB dongle, Bluetooth pads) into menu actions
  with timer-driven, gently accelerating repeat.
- Settings › Controller shows every controller live, which is the quickest way to check the
  dongle on the box.
