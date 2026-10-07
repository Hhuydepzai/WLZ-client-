#!/usr/bin/env bash
set -euo pipefail

OUT="$(realpath "${1:?framework output dir}")"
WORK="$(realpath "${2:?framework work dir}")"
APKTOOL="$(realpath "${3:?apktool jar}")"
mkdir -p "$OUT" "$WORK"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
FOUND=""
SDKMANAGER=""

# Prefer a real Android SDK platform matching Minecraft's target/compile API.
# The Ubuntu android-framework-res package is too old for newer manifests.
for base in "${SDK}" "/opt/android-sdk" "/usr/local/lib/android/sdk" "/usr/lib/android-sdk"; do
  if [ -n "$base" ] && [ -d "$base" ]; then
    if [ -z "$SDK" ]; then
      SDK="$base"
    fi

    # Runner images often ship a versioned cmdline-tools directory instead of
    # the `latest` alias. Find any real sdkmanager binary rather than falling
    # back to the obsolete Ubuntu framework-res package.
    for sm in \
      "$base/cmdline-tools/latest/bin/sdkmanager" \
      "$base/cmdline-tools/bin/sdkmanager" \
      "$base/tools/bin/sdkmanager"; do
      if [ -x "$sm" ]; then
        SDKMANAGER="$sm"
        break 2
      fi
    done

    if [ -z "$SDKMANAGER" ] && [ -d "$base/cmdline-tools" ]; then
      SDKMANAGER="$(find "$base/cmdline-tools" -maxdepth 3 -type f -name sdkmanager -perm -u+x 2>/dev/null | sort -V | tail -n 1 || true)"
      [ -n "$SDKMANAGER" ] && break
    fi
  fi
done

if [ -z "$SDKMANAGER" ]; then
  SDKMANAGER="$(command -v sdkmanager || true)"
fi

# Minecraft's embedded manifest currently targets API 36. Install the matching
# platform when the runner image has sdkmanager but does not preinstall it.
if [ -n "$SDKMANAGER" ]; then
  if [ -z "$SDK" ]; then
    SDK="$(dirname "$(dirname "$(dirname "$SDKMANAGER")")")"
  fi

  PLATFORM="$SDK/platforms/android-36/data/res/framework-res.apk"
  if [ ! -s "$PLATFORM" ]; then
    echo "Installing Android platform 36 for Apktool framework resources..."
    yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
    "$SDKMANAGER" --sdk_root="$SDK" "platforms;android-36"
  fi

  if [ -s "$PLATFORM" ]; then
    FOUND="$PLATFORM"
  fi
fi

# If the runner already contains a platform, prefer the newest installed one.
if [ -z "$FOUND" ]; then
  for base in "${SDK}" "/opt/android-sdk" "/usr/local/lib/android/sdk" "/usr/lib/android-sdk"; do
    if [ -n "$base" ] && [ -d "$base/platforms" ]; then
      FOUND="$(find "$base/platforms" -type f -path "*/data/res/framework-res.apk" 2>/dev/null | sort -V | tail -n 1 || true)"
      [ -n "$FOUND" ] && break
    fi
  done
fi

# Last resort: Ubuntu's framework package. This is only used when no usable
# Android SDK platform can be installed/found.
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
