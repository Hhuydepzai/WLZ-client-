#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$1" "$2"
OUT="$(realpath "$1")"
WORK="$(realpath "$2")"
cd "$WORK"
apt-get download android-framework-res
dpkg-deb -x android-framework-res_*.deb unpacked
cp unpacked/usr/share/android-framework-res/framework-res.apk "$OUT/1.apk"
