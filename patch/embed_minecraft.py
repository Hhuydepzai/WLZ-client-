#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, re, shutil, subprocess, tempfile, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)
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
        return (0 if p.name == "classes.dex" else 1,
                int(re.search(r"(\d+)", p.stem).group(1)) if re.search(r"(\d+)", p.stem) else 0,
                p.as_posix())
    for i, src in enumerate(sorted(dex, key=dex_key), 1):
        dst = payload / ("classes.dex" if i == 1 else f"classes{i}.dex")
        copy_unique(src, dst) if src.resolve() != dst.resolve() else None

    libs = sorted(p for p in payload.rglob("libminecraftpe.so") if p.is_file())
    if libs:
        libdir = payload / "lib" / "arm64-v8a"
        for src in sorted(libs[0].parent.glob("*.so")):
            copy_unique(src, libdir / src.name)

def has_core(apk):
    try:
        with zipfile.ZipFile(apk) as z:
            n = set(z.namelist())
            return "AndroidManifest.xml" in n and MC_SO in n
    except zipfile.BadZipFile:
        return False

def source_apk(payload):
    apks = sorted(payload.rglob("*.apk"))
    if apks:
        core = [p for p in apks if has_core(p)]
        if core:
            named = [p for p in core if p.name.lower() in {"base.apk", "minecraft.apk"}]
            return max(named or core, key=lambda p: p.stat().st_size)
        raise SystemExit("Only split/config APKs found; no APK contains lib/arm64-v8a/libminecraftpe.so.")
    if (payload / "AndroidManifest.xml").exists() and list(payload.glob("classes*.dex")) and (payload / MC_SO).exists():
        return payload / "__reconstructed_minecraft.apk"
    raise SystemExit("Runtime needs AndroidManifest.xml, classes*.dex and lib/arm64-v8a/libminecraftpe.so.")

def rebuild(payload, apk):
    with zipfile.ZipFile(apk, "w") as z:
        for p in sorted(payload.rglob("*")):
            if p.is_file() and p != apk:
                z.write(p, p.relative_to(payload).as_posix(),
                        compress_type=zipfile.ZIP_STORED if p.suffix.lower() in STORED else zipfile.ZIP_DEFLATED)

def patch_manifest(path):
    tree = ET.parse(path)
    root = tree.getroot()
    app = root.find("application")
    if app is None:
        raise SystemExit("Decoded Minecraft manifest has no application.")
    name = f"{{{ANDROID_NS}}}name"
    label = f"{{{ANDROID_NS}}}label"
    icon = f"{{{ANDROID_NS}}}icon"
    round_icon = f"{{{ANDROID_NS}}}roundIcon"
    exported = f"{{{ANDROID_NS}}}exported"
    app.set(label, "WLZ Client")
    app.set(icon, "@drawable/wlz_icon")
    app.set(round_icon, "@drawable/wlz_icon")

    # IMPORTANT: keep Minecraft's original Application class intact.
    # Its startup/bootstrap and integrity/licensing initialization must not be
    # replaced by WLZ. We attach WLZ through a lightweight ContentProvider.
    for a in list(app.findall("activity")):
        for f in list(a.findall("intent-filter")):
            acts = [x.get(name) for x in f.findall("action")]
            cats = [x.get(name) for x in f.findall("category")]
            if "android.intent.action.MAIN" in acts and "android.intent.category.LAUNCHER" in cats:
                a.remove(f)
    def ensure(n, launch=False):
        a = next((x for x in app.findall("activity") if x.get(name) == n), None)
        if a is None:
            a = ET.SubElement(app, "activity", {name: n})
        a.set(exported, "true" if launch else "false")
        return a
    main = ensure("com.wlz.client.MainActivity", True)
    f = ET.SubElement(main, "intent-filter")
    ET.SubElement(f, "action", {name: "android.intent.action.MAIN"})
    ET.SubElement(f, "category", {name: "android.intent.category.LAUNCHER"})
    ensure("com.wlz.client.WlzControlEditorActivity")

    provider_name = f"{{{ANDROID_NS}}}name"
    providers = app.findall("provider")
    if not any(p.get(provider_name) == "com.wlz.client.WlzBootstrapProvider" for p in providers):
        ET.SubElement(app, "provider", {
            provider_name: "com.wlz.client.WlzBootstrapProvider",
            "{" + ANDROID_NS + "}authorities": f"{root.get('package', 'com.mojang.minecraftpe')}.wlzbootstrap",
            "{" + ANDROID_NS + "}exported": "false",
            "{" + ANDROID_NS + "}initOrder": "100"
        })

    tree.write(path, encoding="utf-8", xml_declaration=True)

def next_dex(existing):
    nums = []
    for n in existing:
        m = re.fullmatch(r"classes([0-9]*)\.dex", Path(n).name)
        if m:
            nums.append(1 if not m.group(1) else int(m.group(1)))
    n = max(nums or [1]) + 1
    return "classes.dex" if n == 1 else f"classes{n}.dex"

def inject(helper, base, out):
    with zipfile.ZipFile(helper) as h:
        dex = [n for n in h.namelist() if re.fullmatch(r"classes[0-9]*\.dex", Path(n).name)]
        libs = [(n, h.read(n)) for n in h.namelist() if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(out, "w") as z:
        existing = set(b.namelist())
        for info in b.infolist():
            z.writestr(info, b.read(info.filename),
                       compress_type=zipfile.ZIP_STORED if Path(info.filename).suffix.lower() in STORED else info.compress_type)
        for n in sorted(dex, key=lambda x: (0 if Path(x).name == "classes.dex" else 1, x)):
            name = next_dex(existing)
            z.writestr(name, b"" if False else h_read(helper, n), compress_type=zipfile.ZIP_DEFLATED)
            existing.add(name)
        for n, data in libs:
            if n not in existing:
                z.writestr(n, data, compress_type=zipfile.ZIP_STORED)

def h_read(path, name):
    with zipfile.ZipFile(path) as z:
        return z.read(name)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--wlz-apk", required=True)
    ap.add_argument("--apktool", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    payload = Path(tempfile.mkdtemp(prefix="wlz-payload-"))
    decoded = Path(tempfile.mkdtemp(prefix="wlz-decoded-"))
    rebuilt = Path(tempfile.mktemp(suffix=".apk"))
    try:
        root = Path(a.input).resolve()
        extract_zips(root, payload)
        normalize(payload)
        source = source_apk(payload)
        if source.name == "__reconstructed_minecraft.apk":
            rebuild(payload, source)
        run(["java", "-jar", a.apktool, "d", "-f", str(source), "-o", str(decoded)])
        manifest = decoded / "AndroidManifest.xml"
        if not manifest.exists():
            raise SystemExit("Apktool did not produce AndroidManifest.xml")
        patch_manifest(manifest)
        icon = decoded / "res" / "drawable" / "wlz_icon.xml"
        icon.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2("patch/wlz_icon.xml", icon)
        run(["java", "-jar", a.apktool, "b", str(decoded), "-o", str(rebuilt)])
        out = Path(a.out).resolve()
        tmp = out.with_suffix(".tmp.apk")
        inject(Path(a.wlz_apk).resolve(), rebuilt, tmp)
        tmp.replace(out)
        print("Embedded WLZ Minecraft client:", out)
    finally:
        shutil.rmtree(payload, ignore_errors=True)
        shutil.rmtree(decoded, ignore_errors=True)
        Path(rebuilt).unlink(missing_ok=True)

if __name__ == "__main__":
    main()
