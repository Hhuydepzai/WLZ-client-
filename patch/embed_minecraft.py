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


def patch_manifest_axml(data):
    b = bytearray(data)
    if len(b) < 16:
        raise SystemExit("Manifest too small")

    # Binary XML string-pool patch. Keep the original Minecraft manifest,
    # only change its Application class and visible label. This avoids
    # deleting Minecraft metadata/providers like the earlier helper-manifest
    # replacement did.
    def u32(o): return int.from_bytes(b[o:o+4], "little")
    def put_u32(o,v): b[o:o+4] = int(v).to_bytes(4,"little")
    def read_uleb(o):
        value=0; shift=0
        while True:
            x=b[o]; o+=1
            value |= (x & 0x7f) << shift
            if x < 0x80: return value,o
            shift += 7

    # String pool is normally the first child chunk at offset 8.
    sp = 8
    typ = int.from_bytes(b[sp:sp+2],"little")
    if typ != 0x0001:
        raise SystemExit("Manifest string pool not found")
    sp_size = u32(sp+4)
    count = u32(sp+8)
    flags = u32(sp+16)
    strings_start = u32(sp+20)
    if not (flags & 0x100):
        raise SystemExit("Manifest string pool is not UTF-8")

    offsets = [u32(sp+28+4*i) for i in range(count)]
    base = sp + strings_start

    def get_utf8(idx):
        pos = base + offsets[idx]
        utf16_len,pos2 = read_uleb(pos)
        byte_len,pos3 = read_uleb(pos2)
        raw = bytes(b[pos3:pos3+byte_len])
        return pos, pos2, pos3, byte_len, raw.decode("utf-8","replace")

    def replace_same_slot(idx, new_text):
        pos, p2, p3, old_len, old_text = get_utf8(idx)
        raw = new_text.encode("utf-8")
        if len(raw) > old_len:
            return False
        # Manifest string lengths used here are <128, so one-byte ULEB.
        b[p2] = len(new_text)
        b[p3-1] = len(raw)
        payload_end = p3 + old_len + 1
        b[p3:p3+len(raw)] = raw
        b[p3+len(raw)] = 0
        for j in range(p3+len(raw)+1, payload_end):
            b[j] = 0
        return True

    def find_text(text):
        for i in range(count):
            try:
                if get_utf8(i)[4] == text:
                    return i
            except Exception:
                pass
        return -1

    app_idx=-1
    for i in range(count):
        try:
            s=get_utf8(i)[4]
            if s.endswith("MinecraftApplication"):
                app_idx=i; break
        except Exception:
            continue
    if app_idx < 0:
        raise SystemExit("Original Minecraft Application class not found in manifest")
    if not replace_same_slot(app_idx,"com.wlz.client.WlzApplication"):
        raise SystemExit("WLZ Application class does not fit manifest string slot")

    # Grow the exact label string by one byte: Minecraft -> WLZ Client.
    label_idx=find_text("Minecraft")
    if label_idx >= 0:
        old_rel=offsets[label_idx]
        _,_,_,old_len,_=get_utf8(label_idx)
        raw=b"WLZ Client"
        if len(raw)==old_len:
            replace_same_slot(label_idx,"WLZ Client")
        elif len(raw)==old_len+1:
            insert_at=base+old_rel+2+old_len+1
            b[insert_at:insert_at]=b"\x00"
            for j in range(label_idx+1,count):
                put_u32(sp+28+4*j,u32(sp+28+4*j)+1)
            # Refresh header sizes and pool length.
            put_u32(sp+4,sp_size+1)
            put_u32(4,u32(4)+1)
            # Refresh this string slot after insertion.
            offsets2=[u32(sp+28+4*i) for i in range(count)]
            pbase=sp+strings_start
            p=pbase+offsets2[label_idx]
            b[p]=len("WLZ Client")
            b[p+1]=len(raw)
            b[p+2:p+2+len(raw)]=raw
            b[p+2+len(raw)]=0

    return bytes(b)

def inject(helper, base, out):
    # IMPORTANT: keep the newest Minecraft runtime manifest intact.
    base_entries = read_zip(base)
    helper_entries = read_zip(helper)

    patched_manifest = patch_manifest_axml(base_entries["AndroidManifest.xml"][1])
    out_entries = dict(base_entries)
    out_entries["AndroidManifest.xml"] = (base_entries["AndroidManifest.xml"][0], patched_manifest)

    # Add only WLZ DEX. Do not replace Minecraft resources.arsc/res/ or the
    # original manifest. This preserves the runtime that Minecraft expects.
    existing = set(out_entries)
    helper_dex = [n for n in helper_entries
                  if re.fullmatch(r"classes[0-9]*\.dex", Path(n).name)]
    for name in sorted(helper_dex, key=lambda x:(0 if Path(x).name=="classes.dex" else 1,x)):
        nums=[]
        for n in existing:
            m=re.fullmatch(r"classes([0-9]*)\.dex", Path(n).name)
            if m: nums.append(1 if not m.group(1) else int(m.group(1)))
        next_num=max(nums or [1])+1
        dest="classes.dex" if next_num==1 else f"classes{next_num}.dex"
        out_entries[dest]=helper_entries[name]
        existing.add(dest)

    # Only take WLZ native helper library if it does not collide with the
    # original Minecraft libraries.
    for name,entry in helper_entries.items():
        if name.startswith("lib/arm64-v8a/") and name.endswith(".so") and name not in existing:
            out_entries[name]=entry
            existing.add(name)

    write_zip(out_entries,out)


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
