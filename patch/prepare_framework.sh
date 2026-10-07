#!/usr/bin/env bash
set -euo pipefail
OUT="$1"
WORK="$2"
mkdir -p "$OUT" "$WORK"
cd "$WORK"
apt-get download android-framework-res
dpkg-deb -x android-framework-res_*.deb unpacked
cp unpacked/usr/share/android-framework-res/framework-res.apk "$OUT/1.apk"
