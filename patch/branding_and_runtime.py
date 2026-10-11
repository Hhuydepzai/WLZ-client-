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
    (b"Apollon Client v6.6", b"WLZ Client v2.3.5  "),
    (b"Apollon Chem v6.6", b"WLZ Client v2.3.5"),
    (b"Apollon Client", b"WLZ Client    "),
    (b"Apollon Chem", b"WLZ Client  "),
]
PACKAGE_PATHS = [
    (b"Android/data/com.mojang.minecraftpe", b"Android/data/com.wlzclient.launcher"),
]
SCAN_SUFFIXES = {".dex", ".so", ".arsc", ".xml", ".json", ".txt"}


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
    changed = []
    with zipfile.ZipFile(path, "r") as source:
        bad = source.testzip()
        if bad is not None:
            raise SystemExit(f"Source APK has a ZIP CRC error in {bad}")
        with zipfile.ZipFile(tmp, "w", allowZip64=True) as target:
            for info in source.infolist():
                data = source.read(info.filename)
                suffix = Path(info.filename).suffix.lower()
                if suffix in SCAN_SUFFIXES:
                    data, n_brand = replace_equal_length(data, BRAND)
                    data, n_paths = replace_equal_length(data, PACKAGE_PATHS)
                    if n_brand or n_paths:
                        changed.append(info.filename)
                        brand_count += n_brand
                        path_count += n_paths
                        if info.filename.endswith(".dex") and n_brand + n_paths:
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
        "modified_entries": changed,
        "zip_integrity": "OK",
    }
    print("WLZ branding/path patch:", result, flush=True)
    return result


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 patch/branding_and_runtime.py dist/WLZ-Minecraft-unsigned.apk")
    patch_apk(Path(sys.argv[1]))
