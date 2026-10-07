#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path
import re


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


def has_core(apk):
    try:
        with zipfile.ZipFile(apk) as z:
            names = set(z.namelist())
            return "AndroidManifest.xml" in names and MC_SO in names
    except zipfile.BadZipFile:
        return False


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
            z.writestr(info, data, compress_type=compress)


def run_manifest_editor(manifest_editor, source_apk, out_apk):
    # Keep Minecraft's original MainActivity/launcher intact. We only replace
    # the Application class with WLZ's safe PairIP subclass and register the
    # key-mapping Activity used by the in-game ClickGUI.
    run(
        [
            "java",
            "-jar",
            str(manifest_editor),
            str(source_apk),
            "-o",
            str(out_apk),
            "-an",
            "com.wlz.client.WlzApplication",
            "-act",
            "com.wlz.client.WlzControlEditorActivity:false",
        ]
    )


def inject(helper, base, out):
    base_entries = read_zip(base)
    helper_entries = read_zip(helper)

    existing = set(base_entries)
    out_entries = dict(base_entries)

    helper_dex = [
        n
        for n in helper_entries
        if re.fullmatch(r"classes[0-9]*\.dex", Path(n).name)
    ]
    for index, name in enumerate(
        sorted(helper_dex, key=lambda x: (0 if Path(x).name == "classes.dex" else 1, x)),
        1,
    ):
        nums = []
        for n in existing:
            m = re.fullmatch(r"classes([0-9]*)\.dex", Path(n).name)
            if m:
                nums.append(1 if not m.group(1) else int(m.group(1)))
        next_num = max(nums or [1]) + 1
        dest = "classes.dex" if next_num == 1 else f"classes{next_num}.dex"
        out_entries[dest] = (
            helper_entries[name][0],
            helper_entries[name][1],
        )
        existing.add(dest)

    for name, (info, data) in helper_entries.items():
        if name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
            if name not in existing:
                out_entries[name] = (info, data)
                existing.add(name)

    write_zip(out_entries, out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--wlz-apk", required=True)
    ap.add_argument("--manifest-editor", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    payload = Path(tempfile.mkdtemp(prefix="wlz-payload-"))
    source_rebuilt = Path(tempfile.mktemp(suffix=".apk"))
    manifest_edited = Path(tempfile.mktemp(suffix=".apk"))

    try:
        root = Path(a.input).resolve()
        extract_zips(root, payload)
        normalize(payload)

        source = source_apk(payload)
        if source.name == "__reconstructed_minecraft.apk":
            rebuild(payload, source)
            source = payload / "__reconstructed_minecraft.apk"

        run_manifest_editor(
            Path(a.manifest_editor).resolve(),
            source.resolve(),
            manifest_edited,
        )

        inject(
            Path(a.wlz_apk).resolve(),
            manifest_edited.resolve(),
            source_rebuilt,
        )

        out = Path(a.out).resolve()
        out.parent.mkdir(parents=True, exist_ok=True)
        source_rebuilt.replace(out)
        print("Embedded WLZ Minecraft client:", out)
    finally:
        shutil.rmtree(payload, ignore_errors=True)
        source_rebuilt.unlink(missing_ok=True)
        manifest_edited.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
