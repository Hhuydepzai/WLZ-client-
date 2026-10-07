#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$1" "$2"
OUT="$(realpath "$1")"
WORK="$(realpath "$2")"
SDK="${ANDROID_HOME:-}"
FOUND=""
if [ -n "$SDK" ] && [ -d "$SDK/platforms" ]; then
  FOUND="$(find "$SDK/platforms" -type f -path "*/data/res/framework-res.apk" | sort | tail -n 1)"
fi
if [ -z "$FOUND" ] && [ -n "$SDK" ] && command -v sdkmanager >/dev/null 2>&1; then
  yes | sdkmanager --sdk_root="$SDK" "platforms;android-36" >/dev/null
  FOUND="$(find "$SDK/platforms/android-36" -type f -path "*/data/res/framework-res.apk" | head -n 1)"
fi
if [ -n "$FOUND" ]; then
  cp "$FOUND" "$OUT/1.apk"
else
  cd "$WORK"
  apt-get download android-framework-res
  dpkg-deb -x android-framework-res_*.deb unpacked
  cp unpacked/usr/share/android-framework-res/framework-res.apk "$OUT/1.apk"
fi
