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

# If sdkmanager is unavailable or failed to materialize API 36, download the
# official Android platform archive from Google's repository metadata. This avoids
# the obsolete Ubuntu android-framework-res package, which cannot decode modern
# manifests correctly.
if [ -z "$FOUND" ]; then
  echo "sdkmanager did not provide a usable Android 36 framework; downloading the official platform archive..."
  mkdir -p "$WORK/android-36"
  REPO_XML="$WORK/repository2-3.xml"
  curl -fsSL --retry 4 --retry-delay 2 \
    "https://dl.google.com/android/repository/repository2-3.xml" \
    -o "$REPO_XML"
  test -s "$REPO_XML"

  PLATFORM_URL="$(
    python3 - "$REPO_XML" <<'PY'
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

xml_path = sys.argv[1]
root = ET.parse(xml_path).getroot()

def local(tag):
    return tag.rsplit("}", 1)[-1]

for pkg in root.iter():
    if local(pkg.tag) != "remotePackage" or pkg.attrib.get("path") != "platforms;android-36":
        continue
    archives = []
    for archive in pkg:
        if local(archive.tag) != "archives":
            continue
        for item in archive:
            if local(item.tag) != "archive":
                continue
            complete = None
            host_os = ""
            for child in item:
                if local(child.tag) != "host-os":
                    continue
                host_os = (child.text or "").strip().lower()
            for child in item:
                if local(child.tag) != "complete":
                    continue
                for leaf in child:
                    if local(leaf.tag) == "url" and leaf.text:
                        complete = leaf.text.strip()
            if complete and (not host_os or host_os == "linux"):
                archives.append(complete)
    if archives:
        base = "https://dl.google.com/android/repository/"
        print(urllib.parse.urljoin(base, archives[0]))
        raise SystemExit(0)

raise SystemExit("platforms;android-36 was not found in Google's repository metadata")
PY
  )"
  test -n "$PLATFORM_URL"
  echo "Platform archive: $PLATFORM_URL"

  PLATFORM_ZIP="$WORK/android-platform-36.zip"
  curl -fL --retry 4 --retry-delay 2 "$PLATFORM_URL" -o "$PLATFORM_ZIP"
  test -s "$PLATFORM_ZIP"
  unzip -tq "$PLATFORM_ZIP"

  unzip -q "$PLATFORM_ZIP" -d "$WORK/android-36"
  FOUND="$(find "$WORK/android-36" -type f -path "*/android-36/data/res/framework-res.apk" | head -n 1 || true)"
fi

test -n "$FOUND"
test -s "$FOUND"
echo "Using Android framework: $FOUND"

rm -rf "$OUT"/*
java -jar "$APKTOOL" if "$FOUND" -p "$OUT"
test -s "$OUT/1.apk"
java -jar "$APKTOOL" lf -p "$OUT"
