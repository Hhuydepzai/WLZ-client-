#!/usr/bin/env bash
set -euo pipefail

OUT="$(realpath "${1:?framework output dir}")"
WORK="$(realpath "${2:?framework work dir}")"
APKTOOL="$(realpath "${3:?apktool jar}")"
mkdir -p "$OUT" "$WORK"

SDK="${ANDROID_HOME:-}"
FOUND=""

# GitHub's Android runner normally has framework-res.apk under the SDK.
for base in "${SDK}" "/opt/android-sdk" "/usr/local/lib/android/sdk" "/usr/lib/android-sdk"; do
  if [ -n "$base" ] && [ -d "$base" ]; then
    FOUND="$(find "$base/platforms" "$base/system-images" -type f -path "*/data/res/framework-res.apk" 2>/dev/null | sort -V | tail -n 1 || true)"
    [ -n "$FOUND" ] && break
  fi
done

# Fall back to Ubuntu's framework resource package. Hosted GitHub runners permit
# sudo; the previous implementation tried apt without privileges and died here.
if [ -z "$FOUND" ]; then
  cd "$WORK"
  sudo apt-get update -qq
  apt-cache show android-framework-res >/dev/null 2>&1 || {
    echo "android-framework-res package is unavailable on this runner" >&2
    exit 1
  }
  apt-get download android-framework-res
  PKG="$(ls -1t android-framework-res_*.deb | head -n 1)"
  dpkg-deb -x "$PKG" unpacked
  FOUND="$(find unpacked -type f -name framework-res.apk | head -n 1)"
fi

test -n "$FOUND"
test -s "$FOUND"
echo "Using Android framework: $FOUND"

rm -rf "$OUT"/*
java -jar "$APKTOOL" if "$FOUND" -p "$OUT"
test -s "$OUT/1.apk"
java -jar "$APKTOOL" lf -p "$OUT"
