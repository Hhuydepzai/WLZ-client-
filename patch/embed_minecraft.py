#!/usr/bin/env python3
from __future__ import annotations
import argparse
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)
MC_SO = "lib/arm64-v8a/libminecraftpe.so"

def run(cmd):
    print("+", " ".join(map(str, cmd)))
    subprocess.run(cmd, check=True)

def extract_all_zips(input_dir: Path, out_dir: Path):
    out_dir.mkdir(parents=True, exist_ok=True)
    pending = list(input_dir.rglob("*.zip"))
    seen = set()

    for p in input_dir.rglob("*"):
        if p.is_file() and p.suffix.lower() != ".zip":
            dest = out_dir / p.relative_to(input_dir)
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, dest)

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
                dest = out_dir / rel
                dest.parent.mkdir(parents=True, exist_ok=True)
                with src.open(info) as rf, open(dest, "wb") as wf:
                    shutil.copyfileobj(rf, wf)
        pending.extend([p for p in out_dir.rglob("*.zip") if p.resolve() not in seen])

def apk_has_core(apk: Path) -> bool:
    try:
        with zipfile.ZipFile(apk) as z:
            names = set(z.namelist())
            return "AndroidManifest.xml" in names and MC_SO in names
    except zipfile.BadZipFile:
        return False

def locate_source(payload: Path) -> Path:
    apks = list(payload.rglob("*.apk"))
    if not apks:
        # The user's Bedrock payload may be split across filemc*.zip and
        # assets*.zip archives. extract_all_zips() has already flattened those
        # archives into one APK-shaped directory at this point.
        manifest = payload / "AndroidManifest.xml"
        dex_files = list(payload.glob("classes*.dex"))
        core = payload / MC_SO
        if manifest.exists() and dex_files and core.exists():
            return payload / "__reconstructed_minecraft.apk"
        raise SystemExit(
            "Minecraft payload needs a universal APK or a complete split set "
            "containing AndroidManifest.xml, classes*.dex and lib/arm64-v8a/libminecraftpe.so"
        )

    # Prefer an APK that already contains the complete ARM64 Minecraft runtime.
    # This avoids accidentally selecting a density/config split from an XAPK.
    core = [p for p in apks if apk_has_core(p)]
    if core:
        base_named = [p for p in core if p.name.lower() in {"base.apk", "minecraft.apk"}]
        return max(base_named or core, key=lambda p: p.stat().st_size)

    raise SystemExit(
        "Minecraft input contains only split/config APKs. Provide a universal APK "
        "that contains lib/arm64-v8a/libminecraftpe.so."
    )

def rebuild_raw(payload: Path, out: Path):
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for p in payload.rglob("*"):
            if p.is_file() and p != out:
                z.write(p, p.relative_to(payload).as_posix())

def build_reconstructed_source(payload: Path, out: Path):
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for p in payload.rglob("*"):
            if not p.is_file() or p == out:
                continue
            rel = p.relative_to(payload).as_posix()
            # The source directory is assembled from user-supplied split
            # archives, so preserve every APK entry exactly once.
            z.write(p, rel)

def require_core(payload: Path, source: Path):
    if source.suffix.lower() == ".apk":
        with zipfile.ZipFile(source) as z:
            if MC_SO not in z.namelist():
                raise SystemExit("Minecraft APK missing lib/arm64-v8a/libminecraftpe.so")
            if "AndroidManifest.xml" not in z.namelist():
                raise SystemExit("Minecraft APK missing AndroidManifest.xml")
        return
    candidate = payload / MC_SO
    if not candidate.exists():
        raise SystemExit("Minecraft parts missing lib/arm64-v8a/libminecraftpe.so")

def patch_manifest(path: Path):
    tree = ET.parse(path)
    root = tree.getroot()
    app = root.find("application")
    if app is None:
        raise SystemExit("Minecraft manifest has no application")

    name = f"{{{ANDROID_NS}}}name"
    label = f"{{{ANDROID_NS}}}label"
    icon = f"{{{ANDROID_NS}}}icon"
    round_icon = f"{{{ANDROID_NS}}}roundIcon"
    exported = f"{{{ANDROID_NS}}}exported"

    app.set(label, "WLZ Client")
    app.set(icon, "@drawable/wlz_icon")
    app.set(round_icon, "@drawable/wlz_icon")
    app.set(name, "com.wlz.client.WlzApplication")

    for activity in list(app.findall("activity")):
        for filt in list(activity.findall("intent-filter")):
            actions = [x.get(name) for x in filt.findall("action")]
            cats = [x.get(name) for x in filt.findall("category")]
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in cats:
                activity.remove(filt)

    def ensure_activity(activity_name: str, is_launcher: bool = False):
        node = None
        for a in app.findall("activity"):
            if a.get(name) == activity_name:
                node = a
                break
        if node is None:
            node = ET.Element("activity", {name: activity_name})
            app.append(node)
        node.set(exported, "true" if is_launcher else "false")
        return node

    main = ensure_activity("com.wlz.client.MainActivity", True)
    for filt in list(main.findall("intent-filter")):
        actions = [x.get(name) for x in filt.findall("action")]
        if "android.intent.action.MAIN" in actions:
            main.remove(filt)
    f = ET.SubElement(main, "intent-filter")
    ET.SubElement(f, "action", {name: "android.intent.action.MAIN"})
    ET.SubElement(f, "category", {name: "android.intent.category.LAUNCHER"})

    ensure_activity("com.wlz.client.WlzControlEditorActivity", False)

    tree.write(path, encoding="utf-8", xml_declaration=True)

def next_dex_name(names):
    ids = []
    for n in names:
        m = re.fullmatch(r"classes([0-9]*)\.dex", Path(n).name)
        if m:
            ids.append(1 if not m.group(1) else int(m.group(1)))
    n = max(ids or [1]) + 1
    return "classes.dex" if n == 1 else f"classes{n}.dex"

def inject_wlz(helper_apk: Path, base_apk: Path, out_apk: Path):
    with zipfile.ZipFile(helper_apk) as helper:
        wlz_dex = {
            n: helper.read(n)
            for n in helper.namelist()
            if re.fullmatch(r"classes[0-9]*\\.dex", Path(n).name)
        }
        wlz_libs = {
            n: helper.read(n)
            for n in helper.namelist()
            if n.startswith("lib/arm64-v8a/") and n.endswith(".so")
        }

    with zipfile.ZipFile(base_apk) as base, zipfile.ZipFile(out_apk, "w", zipfile.ZIP_DEFLATED) as out:
        names = set(base.namelist())
        for info in base.infolist():
            out.writestr(info, base.read(info.filename))
        # Preserve every WLZ dex shard. Injecting only classes.dex would drop
        # the Java classes in classes2.dex from the current helper build.
        existing = set(names)
        for _, data in sorted(wlz_dex.items()):
            name = next_dex_name(existing)
            out.writestr(name, data)
            existing.add(name)
        for n, data in wlz_libs.items():
            if n not in names:
                out.writestr(n, data)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--wlz-apk", required=True)
    ap.add_argument("--apktool", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    input_dir = Path(args.input).resolve()
    out = Path(args.out).resolve()
    payload = Path(tempfile.mkdtemp(prefix="wlz-mc-payload-"))
    decoded = Path(tempfile.mkdtemp(prefix="wlz-mc-decoded-"))
    rebuilt = Path(tempfile.mktemp(suffix=".apk"))

    try:
        extract_all_zips(input_dir, payload)
        source = locate_source(payload)
        if source == payload / "__reconstructed_minecraft.apk":
            build_reconstructed_source(payload, source)
        require_core(payload, source)

        run(["java", "-jar", args.apktool, "d", "-f", str(source), "-o", str(decoded)])
        manifest = decoded / "AndroidManifest.xml"
        if not manifest.exists():
            raise SystemExit("Decoded Minecraft APK has no AndroidManifest.xml")

        patch_manifest(manifest)
        icon_dst = decoded / "res" / "drawable" / "wlz_icon.xml"
        icon_dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(Path("patch/wlz_icon.xml"), icon_dst)

        run(["java", "-jar", args.apktool, "b", str(decoded), "-o", str(rebuilt)])
        out.parent.mkdir(parents=True, exist_ok=True)
        temp = out.with_suffix(".tmp.apk")
        inject_wlz(Path(args.wlz_apk).resolve(), rebuilt, temp)
        shutil.move(temp, out)
        print("Embedded WLZ Minecraft client:", out)
    finally:
        shutil.rmtree(payload, ignore_errors=True)
        shutil.rmtree(decoded, ignore_errors=True)
        Path(rebuilt).unlink(missing_ok=True)

if __name__ == "__main__":
    main()
