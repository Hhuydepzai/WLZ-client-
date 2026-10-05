#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)


def run(cmd, cwd=None):
    print("+", " ".join(map(str, cmd)))
    subprocess.run(cmd, cwd=cwd, check=True)


def extract_all_zips(input_dir: Path, out_dir: Path):
    out_dir.mkdir(parents=True, exist_ok=True)

    # Copy non-zip files first.
    for p in input_dir.rglob("*"):
        if p.is_file() and p.suffix.lower() != ".zip":
            rel = p.relative_to(input_dir)
            dest = out_dir / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, dest)

    # Recursively extract every uploaded zip part.
    pending = list(input_dir.rglob("*.zip"))
    seen = set()
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

        # Nested zip files extracted from this archive become pending parts.
        pending.extend(out_dir.rglob("*.zip"))


def locate_source(payload: Path) -> Path:
    apks = sorted(payload.rglob("*.apk"), key=lambda p: p.stat().st_size, reverse=True)
    if apks:
        return apks[0]

    manifest = next(iter(payload.rglob("AndroidManifest.xml")), None)
    dex = next(iter(payload.rglob("classes.dex")), None)
    if not manifest or not dex:
        raise SystemExit("Minecraft payload must contain an APK or AndroidManifest.xml + classes.dex")

    # Rebuild a raw APK if the uploaded parts were extracted from an APK.
    rebuilt = payload / "__reconstructed_minecraft.apk"
    with zipfile.ZipFile(rebuilt, "w", zipfile.ZIP_DEFLATED) as out:
        for p in payload.rglob("*"):
            if not p.is_file() or p == rebuilt:
                continue
            out.write(p, p.relative_to(payload).as_posix())
    return rebuilt


def patch_manifest(manifest_path: Path):
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    app = root.find("application")
    if app is None:
        raise SystemExit("Minecraft manifest has no <application>")

    name_attr = f"{{{ANDROID_NS}}}name"
    label_attr = f"{{{ANDROID_NS}}}label"
    icon_attr = f"{{{ANDROID_NS}}}icon"
    round_icon_attr = f"{{{ANDROID_NS}}}roundIcon"
    exported_attr = f"{{{ANDROID_NS}}}exported"

    app.set(label_attr, "WLZ Client")
    app.set(icon_attr, "@drawable/wlz_icon")
    app.set(round_icon_attr, "@drawable/wlz_icon")

    # Remove the old launcher intent filter so WLZ owns the entry point.
    for activity in list(app.findall("activity")):
        for filt in list(activity.findall("intent-filter")):
            actions = [x.get(name_attr) for x in filt.findall("action")]
            cats = [x.get(name_attr) for x in filt.findall("category")]
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in cats:
                activity.remove(filt)

    # Avoid duplicate declaration if the helper was already inserted.
    existing = None
    for activity in app.findall("activity"):
        if activity.get(name_attr) == "com.wlz.client.MainActivity":
            existing = activity
            break

    if existing is None:
        existing = ET.Element("activity")
        app.append(existing)
    existing.set(name_attr, "com.wlz.client.MainActivity")
    existing.set(exported_attr, "true")

    for filt in list(existing.findall("intent-filter")):
        actions = [x.get(name_attr) for x in filt.findall("action")]
        if "android.intent.action.MAIN" in actions:
            existing.remove(filt)

    filt = ET.Element("intent-filter")
    ET.SubElement(filt, "action", {name_attr: "android.intent.action.MAIN"})
    ET.SubElement(filt, "category", {name_attr: "android.intent.category.LAUNCHER"})
    existing.append(filt)

    def ensure_activity(name: str):
        for activity in app.findall("activity"):
            if activity.get(name_attr) == name:
                return
        node = ET.Element("activity")
        node.set(name_attr, name)
        node.set(exported_attr, "false")
        app.append(node)

    ensure_activity("com.wlz.client.WlzControlEditorActivity")

    tree.write(manifest_path, encoding="utf-8", xml_declaration=True)


def next_dex_name(apk: zipfile.ZipFile) -> str:
    ids = []
    for n in apk.namelist():
        m = re.fullmatch(r"classes(\\d*)\\.dex", Path(n).name)
        if m:
            ids.append(1 if m.group(1) == "" else int(m.group(1)))
    n = max(ids or [1]) + 1
    return "classes.dex" if n == 1 else f"classes{n}.dex"


def inject_wlz(helper_apk: Path, built_apk: Path, out_apk: Path, icon_src: Path):
    temp_unsigned = out_apk.with_suffix(".unsigned.apk")

    with zipfile.ZipFile(helper_apk) as helper:
        wlz_dex = helper.read("classes.dex")
        wlz_libs = {
            name: helper.read(name)
            for name in helper.namelist()
            if name.startswith("lib/arm64-v8a/") and name.endswith(".so")
        }

    # Add WLZ dex/native libs and icon to the rebuilt Minecraft APK.
    with zipfile.ZipFile(built_apk, "r") as base, zipfile.ZipFile(
        temp_unsigned, "w", zipfile.ZIP_DEFLATED
    ) as out:
        names = set(base.namelist())
        dex_name = next_dex_name(base)

        for info in base.infolist():
            data = base.read(info.filename)
            out.writestr(info, data)

        out.writestr(dex_name, wlz_dex)

        for name, data in wlz_libs.items():
            if name not in names:
                out.writestr(name, data)

    out_apk.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(temp_unsigned, out_apk)
    temp_unsigned.unlink(missing_ok=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--wlz-apk", required=True)
    ap.add_argument("--apktool", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    input_dir = Path(args.input).resolve()
    wlz_apk = Path(args.wlz_apk).resolve()
    out = Path(args.out).resolve()
    icon_src = Path("patch/wlz_icon.xml").resolve()

    if not input_dir.exists():
        raise SystemExit(f"Missing Minecraft input directory: {input_dir}")

    with tempfile.TemporaryDirectory(prefix="wlz-mc-") as td:
        work = Path(td)
        payload = work / "payload"
        decoded = work / "decoded"
        rebuilt = work / "rebuilt.apk"

        extract_all_zips(input_dir, payload)
        source = locate_source(payload)

        run(["java", "-jar", args.apktool, "d", "-f", str(source), "-o", str(decoded)])

        manifest = decoded / "AndroidManifest.xml"
        patch_manifest(manifest)

        icon_dst = decoded / "res" / "drawable" / "wlz_icon.xml"
        icon_dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(icon_src, icon_dst)

        run(["java", "-jar", args.apktool, "b", str(decoded), "-o", str(rebuilt)])

        inject_wlz(wlz_apk, rebuilt, out, icon_src)

    print(f"Embedded WLZ Minecraft client: {out}")


if __name__ == "__main__":
    main()
