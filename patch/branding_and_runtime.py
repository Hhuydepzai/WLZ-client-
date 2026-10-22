#!/usr/bin/env python3
"""Conservative WLZ runtime branding/path patch.

Only equal-length text substitutions are performed so DEX string offsets,
native ELF offsets and resource-table indices remain stable. Android's DEX
signature/checksum fields are regenerated after each DEX modification.
The script does not touch licensing code, executable logic, or native symbols.
"""
from __future__ import annotations

import hashlib
import os
import re
import struct
import sys
import tempfile
import zipfile
import zlib
from pathlib import Path

BRAND = [
    (b"Apollon Client v6.6", b"WLZ Client v2.4.0  "),
    (b"Apollon Chem v6.6", b"WLZ Client v2.4.0"),
    (b"Apollon Client", b"WLZ Client    "),
    (b"Apollon Chem", b"WLZ Client  "),
]
# The side-by-side build uses a distinct application ID and storage folder.
# Fully-qualified Minecraft Java class names are intentionally not rewritten.
PACKAGE_PATHS = [
    (b"Android/data/com.mojang.minecraftpe", b"Android/data/com.wlzclient.launcher"),
    (b"com.mojang.minecraftpe.fileprovider", b"com.wlzclient.launcher.fileprovider"),
    (b"com.mojang.minecraftpe.firebaseinitprovider", b"com.wlzclient.launcher.firebaseinitprovider"),
    (b"com.mojang.minecraftpe.playgamesinitprovider", b"com.wlzclient.launcher.playgamesinitprovider"),
    (b"com.mojang.minecraftpe.androidx-startup", b"com.wlzclient.launcher.androidx-startup"),
]

PACKAGE_ID_OLD = b"com.mojang.minecraftpe"
PACKAGE_ID_NEW = b"com.wlzclient.launcher"
if len(PACKAGE_ID_OLD) != len(PACKAGE_ID_NEW):
    raise RuntimeError("Side-by-side package IDs must have equal byte length")


def replace_package_identity(data: bytes) -> tuple[bytes, int]:
    # Replace only an exact dotted application ID, not prefixes of class names
    # like com.mojang.minecraftpe.MainActivity or DEX descriptors.
    total = 0
    pattern = rb"(?<![A-Za-z0-9_.$/])" + re.escape(PACKAGE_ID_OLD) + rb"(?![A-Za-z0-9_.$/])"
    data, n = re.subn(pattern, PACKAGE_ID_NEW, data)
    total += n

    # Also handle the same identifier stored as UTF-16LE in resource/string pools.
    old16 = b"".join(bytes((x, 0)) for x in PACKAGE_ID_OLD)
    new16 = b"".join(bytes((x, 0)) for x in PACKAGE_ID_NEW)
    pattern16 = rb"(?<![A-Za-z0-9_.$/]\\x00)" + re.escape(old16) + rb"(?![A-Za-z0-9_.$/]\\x00)"
    data, n = re.subn(pattern16, new16, data)
    total += n
    return data, total
SCAN_SUFFIXES = {".dex", ".so", ".arsc", ".xml", ".json", ".txt"}

# These names are existing Apollon icon resources. Replace their PNG bytes
# without changing resource IDs or the compiled resources.arsc table.
ICON_SIZES = {
    "res/drawable-mdpi-v4/icon.png": 72,
    "res/drawable-hdpi-v4/icon.png": 96,
    "res/drawable-xhdpi-v4/icon.png": 144,
    "res/drawable-xxhdpi-v4/icon.png": 192,
    "res/drawable-xxxhdpi-v4/icon.png": 288,
}


def _png_chunk(kind: bytes, data: bytes) -> bytes:
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def icon_size_for_path(name: str):
    if name in ICON_SIZES:
        return ICON_SIZES[name]
    lower = name.lower()
    base = Path(lower).name
    if not lower.startswith("res/") or not lower.endswith(".png"):
        return None
    if base not in {"icon.png", "ic_launcher.png", "launcher_icon.png", "app_icon.png", "launcher.png"}:
        return None
    if "xxxhdpi" in lower: return 288
    if "xxhdpi" in lower: return 192
    if "xhdpi" in lower: return 144
    if "hdpi" in lower: return 96
    if "mdpi" in lower: return 72
    return 192


def make_wlz_icon_png(size: int) -> bytes:
    """Create a compact orange WLZ + crown app icon using only stdlib."""
    dark = (8, 9, 12, 255)
    orange = (255, 112, 0, 255)
    gold = (255, 190, 55, 255)
    black = (0, 0, 0, 255)
    pixels = bytearray(size * size * 4)

    # Soft dark field with a restrained amber glow/ring.
    cx = cy = (size - 1) / 2.0
    radius = size * 0.43
    for y in range(size):
        for x in range(size):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            glow = max(0.0, 1.0 - d / (size * 0.78))
            lift = int(14 * glow * glow)
            color = (dark[0] + lift, dark[1] + int(lift * .45), dark[2], 255)
            if abs(d - radius) < max(1.4, size * .012):
                color = orange
            q = (y * size + x) * 4
            pixels[q:q+4] = bytes(color)

    def paint_polygon(points, color):
        pts = [(px * size, py * size) for px, py in points]
        min_y = max(0, int(min(y for _, y in pts)))
        max_y = min(size - 1, int(max(y for _, y in pts)) + 1)
        min_x = max(0, int(min(x for x, _ in pts)))
        max_x = min(size - 1, int(max(x for x, _ in pts)) + 1)
        for iy in range(min_y, max_y + 1):
            yy = iy + .5
            intersections = []
            for i in range(len(pts)):
                x1, y1 = pts[i]
                x2, y2 = pts[(i + 1) % len(pts)]
                if (y1 <= yy < y2) or (y2 <= yy < y1):
                    intersections.append(x1 + (yy-y1) * (x2-x1) / (y2-y1))
            intersections.sort()
            for j in range(0, len(intersections)-1, 2):
                xa = max(min_x, int(intersections[j]))
                xb = min(max_x, int(intersections[j+1]) + 1)
                for ix in range(xa, xb + 1):
                    q = (iy * size + ix) * 4
                    pixels[q:q+4] = bytes(color)

    # Crown silhouette and inset gold highlight.
    paint_polygon([(0.31,.16),(.43,.28),(.50,.12),(.57,.28),(.69,.16),(.65,.39),(.35,.39)], black)
    paint_polygon([(0.33,.17),(.43,.28),(.50,.14),(.57,.28),(.67,.17),(.63,.36),(.37,.36)], orange)
    paint_polygon([(0.38,.22),(.44,.29),(.50,.18),(.56,.29),(.62,.22),(.60,.32),(.40,.32)], gold)

    # Thick angular WLZ wordmark; dark under-stroke adds separation at small size.
    glyphs = [
        [(0.13,.46),(.22,.46),(.26,.61),(.32,.49),(.38,.61),(.42,.46),(.50,.46),(.43,.79),(.35,.79),(.32,.69),(.28,.79),(.21,.79)],
        [(.47,.46),(.56,.46),(.56,.71),(.63,.71),(.63,.80),(.47,.80)],
        [(.61,.46),(.86,.46),(.86,.53),(.70,.71),(.86,.71),(.86,.80),(.60,.80),(.60,.73),(.76,.54),(.61,.54)],
    ]
    for poly in glyphs:
        expanded = [(0.50+(x-.50)*1.045, 0.63+(y-.63)*1.045) for x,y in poly]
        paint_polygon(expanded, black)
        paint_polygon(poly, orange)
    # Gold cut facets, kept sparse to avoid a cluttered thumbnail.
    paint_polygon([(.16,.49),(.21,.49),(.25,.62),(.22,.62)], gold)
    paint_polygon([(.65,.49),(.81,.49),(.78,.52),(.66,.52)], gold)

    raw = bytearray()
    for y in range(size):
        raw.append(0)  # PNG filter: None
        raw.extend(pixels[y * size * 4:(y + 1) * size * 4])
    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + _png_chunk(b"IHDR", header)
            + _png_chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + _png_chunk(b"IEND", b""))


def patch_dex_header(data: bytearray) -> None:
    if not data.startswith(b"dex\n") or len(data) < 112:
        return
    # DEX header: SHA-1 signature over bytes [32:], then Adler-32 over [12:].
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into("<I", data, 8, zlib.adler32(data[12:]) & 0xFFFFFFFF)


def replace_equal_length(data: bytes, replacements: list[tuple[bytes, bytes]]) -> tuple[bytes, int]:
    out = data
    total = 0
    for old, new in replacements:
        if len(old) != len(new):
            raise ValueError(f"Replacement changes byte length: {old!r} -> {new!r}")
        for old_bytes, new_bytes in (
            (old, new),
            (old.decode("ascii").encode("utf-16le"), new.decode("ascii").encode("utf-16le")),
        ):
            n = out.count(old_bytes)
            if n:
                out = out.replace(old_bytes, new_bytes)
                total += n
    return out, total


def patch_apk(path: Path) -> dict:
    if not path.is_file():
        raise SystemExit(f"APK not found: {path}")
    tmp = path.with_suffix(path.suffix + ".debrand.tmp")
    brand_count = 0
    path_count = 0
    package_count = 0
    icon_count = 0
    changed = []
    with zipfile.ZipFile(path, "r") as source:
        bad = source.testzip()
        if bad is not None:
            raise SystemExit(f"Source APK has a ZIP CRC error in {bad}")
        with zipfile.ZipFile(tmp, "w", allowZip64=True) as target:
            for info in source.infolist():
                data = source.read(info.filename)
                suffix = Path(info.filename).suffix.lower()
                icon_size = icon_size_for_path(info.filename)
                if icon_size is not None:
                    data = make_wlz_icon_png(icon_size)
                    icon_count += 1
                    changed.append(info.filename)
                if suffix in SCAN_SUFFIXES:
                    data, n_package = replace_package_identity(data)
                    data, n_brand = replace_equal_length(data, BRAND)
                    data, n_paths = replace_equal_length(data, PACKAGE_PATHS)
                    if n_brand or n_paths or n_package:
                        changed.append(info.filename)
                        brand_count += n_brand
                        path_count += n_paths
                        package_count += n_package
                        if info.filename.endswith(".dex") and n_brand + n_paths + n_package:
                            dex = bytearray(data)
                            patch_dex_header(dex)
                            data = bytes(dex)
                out_info = zipfile.ZipInfo(info.filename, date_time=info.date_time)
                out_info.compress_type = info.compress_type
                out_info.external_attr = info.external_attr
                out_info.create_system = info.create_system
                target.writestr(out_info, data, compress_type=info.compress_type, compresslevel=6)
    os.replace(tmp, path)
    with zipfile.ZipFile(path, "r") as check:
        bad = check.testzip()
        if bad is not None:
            raise SystemExit(f"Debranded APK failed ZIP CRC check at {bad}")
        names = set(check.namelist())
        required = ["AndroidManifest.xml", "resources.arsc", "lib/arm64-v8a/libminecraftpe.so"]
        missing = [name for name in required if name not in names]
        if missing or not any(name.startswith("assets/") for name in names):
            raise SystemExit(f"Debrand step lost core Minecraft data: {missing or ['assets/*']}")
        # Catch stale header text before the APK is signed.
        remaining = []
        for name in names:
            if Path(name).suffix.lower() not in SCAN_SUFFIXES:
                continue
            data = check.read(name)
            old_brand_strings = (
                b"Apollon Client v6.6", b"Apollon Client",
                b"Apollon Chem v6.6", b"Apollon Chem",
            )
            if any(token in data for token in old_brand_strings):
                remaining.append(name)
        if brand_count == 0:
            raise SystemExit("No Apollon Client title string was found in scanned runtime files; refusing to claim debranding succeeded.")
        if remaining:
            raise SystemExit("Apollon branding remains in: " + ", ".join(sorted(remaining)[:10]))
    result = {
        "apk": str(path),
        "title_replacements": brand_count,
        "package_path_replacements": path_count,
        "package_identity_replacements": package_count,
        "application_id": "com.wlzclient.launcher",
        "launcher_icons_replaced": icon_count,
        "modified_entries": changed,
        "zip_integrity": "OK",
    }
    if icon_count == 0:
        print("WARNING: no known Apollon launcher PNG path matched; resources.arsc was preserved.", flush=True)
    print("WLZ branding/path patch:", result, flush=True)
    return result


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 patch/branding_and_runtime.py dist/WLZ-Minecraft-unsigned.apk")
    patch_apk(Path(sys.argv[1]))
