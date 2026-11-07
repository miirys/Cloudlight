#!/usr/bin/env bash
# Generates the decoder stress clips bundled into the probe APK.
# Streams are shaped like a game stream: one keyframe, then P-frames only, no B-frames,
# at bitrates at or above what the real client will request.
set -euo pipefail
out="$(cd "$(dirname "$0")" && pwd)/clips"
mkdir -p "$out"
src1="testsrc2=size=1920x1080:rate=120,noise=alls=12:allf=t+u"
src4="testsrc2=size=3840x2160:rate=60,noise=alls=12:allf=t+u"
ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }

ff -f lavfi -i "$src1" -frames:v 120 -pix_fmt yuv420p -c:v libx265 -preset ultrafast -tune zerolatency \
  -b:v 100M -maxrate 100M -bufsize 3M -x265-params "bframes=0:keyint=1000:repeat-headers=1" -tag:v hvc1 \
  "$out/hevc_1080p120.mp4"
ff -f lavfi -i "$src4" -frames:v 60 -pix_fmt yuv420p -c:v libx265 -preset ultrafast -tune zerolatency \
  -b:v 150M -maxrate 150M -bufsize 5M -x265-params "bframes=0:keyint=1000:repeat-headers=1" -tag:v hvc1 \
  "$out/hevc_2160p60.mp4"
# SVT-AV1 caps CBR at 100 Mbps.
ff -f lavfi -i "$src1" -frames:v 120 -pix_fmt yuv420p -c:v libsvtav1 -preset 12 -b:v 100M \
  -svtav1-params "pred-struct=1:keyint=1000:rc=2" "$out/av1_1080p120.mp4"
ff -f lavfi -i "$src4" -frames:v 60 -pix_fmt yuv420p -c:v libsvtav1 -preset 12 -b:v 100M \
  -svtav1-params "pred-struct=1:keyint=1000:rc=2" "$out/av1_2160p60.mp4"
ls -la "$out"
