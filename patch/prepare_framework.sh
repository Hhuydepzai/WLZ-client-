#!/usr/bin/env bash
set -euo pipefail

OUT="$(realpath "${1:?framework output dir}")"
WORK="$(realpath "${2:?framework work dir}")"
APKTOOL="$(realpath "${3:?apktool jar}")"
mkdir -p "$OUT" "$WORK"

SDK="${ANDROID_HOME:-}"
FOUND=""
if [ -n "$SDK" ] && [ -d "$SDK/platforms" ]; then
  FOUND="$(find "$SDK/platforms" -type f -path "*/data/res/framework-res.apk" | sort | tail -n 1)"
fi

if [ -z "$FOUND" ] && [ -n "$SDK" ] && command -v sdkmanager >/dev/null 2>&1; then
  yes | sdkmanager --sdk_root="$SDK" "platforms;android-36" >/dev/null
  FOUND="$(find "$SDK/platforms/android-36" -type f -path "*/data/res/framework-res.apk" | head -n 1)"
fi

if [ -z "$FOUND" ]; then
  cd "$WORK"
  apt-get update -qq
  apt-get download android-framework-res
  dpkg-deb -x android-framework-res_*.deb unpacked
  FOUND="$(find unpacked -type f -name framework-res.apk | head -n 1)"
fi

test -n "$FOUND"
test -s "$FOUND"
echo "Using Android framework: $FOUND"

# Do not manually copy framework-res.apk. Apktool assigns framework package IDs
# and stores the framework in the format it expects.
rm -rf "$OUT"/*
java -jar "$APKTOOL" if "$FOUND" -p "$OUT"
test -s "$OUT/1.apk"

echo "Installed Apktool frameworks:"
java -jar "$APKTOOL" lf -p "$OUT"
