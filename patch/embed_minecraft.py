#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
import tempfile
import zipfile
from copy import copy
from pathlib import Path


MC_SO = "lib/arm64-v8a/libminecraftpe.so"
STORED = {".so", ".dex", ".arsc", ".apk"}


def run(cmd):
    print("+", " ".join(map(str, cmd)), flush=True)
    subprocess.run(cmd, check=True)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def copy_unique(src, dst):
    dst.parent.mkdir(parents=True, exist_ok=True)
    if not dst.exists():
        shutil.copy2(src, dst)
        return
    if src.stat().st_size == dst.stat().st_size and sha256(src) == sha256(dst):
        return
    raise SystemExit(f"Conflicting duplicate runtime file: {dst}")


def extract_zips(root, out):
    out.mkdir(parents=True, exist_ok=True)
    pending = sorted(root.rglob("*.zip"))
    seen = set()

    for p in sorted(root.rglob("*")):
        if p.is_file() and p.suffix.lower() != ".zip":
            copy_unique(p, out / p.relative_to(root))

    while pending:
        z = pending.pop(0).resolve()
        if z in seen:
            continue
        seen.add(z)
        with zipfile.ZipFile(z) as src:
            for info in src.infolist():
                if info.is_dir():
                    continue
                rel = Path(info.filename)
                if rel.is_absolute() or ".." in rel.parts:
                    continue
                dst = out / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                tmp = dst.with_name(dst.name + ".part")
                with src.open(info) as rf, open(tmp, "wb") as wf:
                    shutil.copyfileobj(rf, wf)
                if dst.exists():
                    if tmp.stat().st_size == dst.stat().st_size and sha256(tmp) == sha256(dst):
                        tmp.unlink()
                    else:
                        tmp.unlink(missing_ok=True)
                        raise SystemExit(f"Conflicting duplicate runtime file: {dst}")
                else:
                    tmp.replace(dst)
        pending += [p for p in sorted(out.rglob("*.zip")) if p.resolve() not in seen]


def normalize(payload):
    manifest = payload / "AndroidManifest.xml"
    if not manifest.exists():
        found = sorted(payload.rglob("AndroidManifest.xml"))
        if found:
            copy_unique(found[0], manifest)

    dex = [p for p in payload.rglob("classes*.dex") if p.is_file()]

    def dex_key(p):
        m = re.search(r"(\d+)", p.stem)
        return (
            0 if p.name == "classes.dex" else 1,
            int(m.group(1)) if m else 0,
            p.as_posix(),
        )

    for i, src in enumerate(sorted(dex, key=dex_key), 1):
        dst = payload / ("classes.dex" if i == 1 else f"classes{i}.dex")
        if src.resolve() != dst.resolve():
            copy_unique(src, dst)

    libs = sorted(p for p in payload.rglob("libminecraftpe.so") if p.is_file())
    if libs:
        libdir = payload / "lib" / "arm64-v8a"
        for src in sorted(libs[0].parent.glob("*.so")):
            copy_unique(src, libdir / src.name)


def prune_duplicate_runtime_files(payload):
    libdir = payload / "lib" / "arm64-v8a"
    if not libdir.exists():
        return

    # Keep one canonical copy under Android's standard lib/<abi>/ location.
    # The extracted runtime sometimes contains the same .so at the root and
    # under arm64-v8a/, which previously inflated the final APK by hundreds
    # of megabytes and could confuse package/runtime loading.
    for p in sorted(payload.rglob("*.so")):
        if p == libdir / p.name:
            continue
        canonical = libdir / p.name
        if canonical.exists() and p.stat().st_size == canonical.stat().st_size:
            if sha256(p) == sha256(canonical):
                p.unlink()

    # Remove now-empty duplicate ABI directories.
    for d in [payload / "arm64-v8a"]:
        if d.exists():
            shutil.rmtree(d, ignore_errors=True)


def source_apk(payload):
    apks = sorted(payload.rglob("*.apk"))
    if apks:
        core = [p for p in apks if has_core(p)]
        if core:
            named = [p for p in core if p.name.lower() in {"base.apk", "minecraft.apk"}]
            return max(named or core, key=lambda p: p.stat().st_size)
        raise SystemExit(
            "Only split/config APKs found; no APK contains "
            "lib/arm64-v8a/libminecraftpe.so."
        )

    if (
        (payload / "AndroidManifest.xml").exists()
        and list(payload.glob("classes*.dex"))
        and (payload / MC_SO).exists()
    ):
        return payload / "__reconstructed_minecraft.apk"

    raise SystemExit(
        "Runtime needs AndroidManifest.xml, classes*.dex and "
        "lib/arm64-v8a/libminecraftpe.so."
    )


def rebuild(payload, apk):
    with zipfile.ZipFile(apk, "w") as z:
        for p in sorted(payload.rglob("*")):
            if p.is_file() and p != apk:
                z.write(
                    p,
                    p.relative_to(payload).as_posix(),
                    compress_type=(
                        zipfile.ZIP_STORED
                        if p.suffix.lower() in STORED
                        else zipfile.ZIP_DEFLATED
                    ),
                )


def read_zip(path):
    with zipfile.ZipFile(path) as z:
        return {info.filename: (info, z.read(info.filename)) for info in z.infolist()}


def write_zip(entries, out):
    with zipfile.ZipFile(out, "w") as z:
        for name, (info, data) in entries.items():
            compress = (
                zipfile.ZIP_STORED
                if Path(name).suffix.lower() in STORED
                else info.compress_type
            )
            out_info = copy(info)
            out_info.filename = name
            z.writestr(out_info, data, compress_type=compress)


def inject(helper, base, out):
    base_entries = read_zip(base)
    helper_entries = read_zip(helper)

    helper_manifest = helper_entries.get("AndroidManifest.xml")
    if helper_manifest is None:
        raise SystemExit("WLZ helper APK has no compiled AndroidManifest.xml")

    # The old build ran ManifestEditor against the reconstructed Minecraft
    # manifest, which left resource-table references behind even though the
    # reconstructed package did not contain resources.arsc. Use the helper
    # manifest that Gradle compiled from patch/AndroidManifest.xml instead.
    # It contains literal WLZ labeling and only system-resource references.
    out_entries = dict(base_entries)
    out_entries["AndroidManifest.xml"] = helper_manifest
    existing = set(out_entries)

    # Keep the newest WLZ code/features unchanged, but also package the
    # compiled WLZ resources. The helper resource bundle contains the WLZ
    # v0.6.4 icon/splash and its resource table. The previous build dropped
    # these entries, so the APK installed with a generic Android icon.
    if "resources.arsc" in helper_entries:
        out_entries["resources.arsc"] = helper_entries["resources.arsc"]
        existing.add("resources.arsc")
    for name, entry in helper_entries.items():
        if name.startswith("res/"):
            out_entries[name] = entry
            existing.add(name)

    helper_dex = [
        n
        for n in helper_entries
        if re.fullmatch(r"classes[0-9]*\.dex", Path(n).name)
    ]
    for name in sorted(
        helper_dex,
        key=lambda x: (0 if Path(x).name == "classes.dex" else 1, x),
    ):
        nums = []
        for n in existing:
            m = re.fullmatch(r"classes([0-9]*)\.dex", Path(n).name)
            if m:
                nums.append(1 if not m.group(1) else int(m.group(1)))
        next_num = max(nums or [1]) + 1
        dest = "classes.dex" if next_num == 1 else f"classes{next_num}.dex"
        out_entries[dest] = helper_entries[name]
        existing.add(dest)

    for name, entry in helper_entries.items():
        if name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
            if name not in existing:
                out_entries[name] = entry
                existing.add(name)

    write_zip(out_entries, out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--wlz-apk", required=True)
    # Kept for workflow compatibility. The final package no longer needs
    # ManifestEditor because its manifest is taken from the compiled helper.
    ap.add_argument("--manifest-editor", required=False)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    payload = Path(tempfile.mkdtemp(prefix="wlz-payload-"))
    source_rebuilt = Path(tempfile.mktemp(suffix=".apk"))

    try:
        root = Path(a.input).resolve()
        extract_zips(root, payload)
        normalize(payload)
        prune_duplicate_runtime_files(payload)

        source = source_apk(payload)
        if source.name == "__reconstructed_minecraft.apk":
            rebuild(payload, source)
            source = payload / "__reconstructed_minecraft.apk"

        out = Path(a.out).resolve()
        out.parent.mkdir(parents=True, exist_ok=True)
        inject(
            Path(a.wlz_apk).resolve(),
            source.resolve(),
            source_rebuilt,
        )
        source_rebuilt.replace(out)

        print("Embedded WLZ Minecraft client:", out)
    finally:
        shutil.rmtree(payload, ignore_errors=True)
        source_rebuilt.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
