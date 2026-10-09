#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
import struct
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


def has_core(apk):
    try:
        with zipfile.ZipFile(apk) as z:
            names = set(z.namelist())
            return (
                MC_SO in names
                and any(re.fullmatch(r"classes[0-9]*\.dex", n) for n in names)
            )
    except (OSError, zipfile.BadZipFile):
        return False


def validate_full_runtime(apk):
    try:
        with zipfile.ZipFile(apk) as z:
            names = set(z.namelist())
    except (OSError, zipfile.BadZipFile) as exc:
        raise SystemExit(f"Source Minecraft APK is not a valid ZIP/APK: {exc}")

    missing = []
    if "AndroidManifest.xml" not in names:
        missing.append("AndroidManifest.xml")
    if "resources.arsc" not in names:
        missing.append("resources.arsc")
    if not any(n.startswith("assets/") for n in names):
        missing.append("assets/*")
    if MC_SO not in names:
        missing.append(MC_SO)
    if not any(re.fullmatch(r"classes[0-9]*\.dex", n) for n in names):
        missing.append("classes*.dex")

    if missing:
        raise SystemExit(
            "Refusing to build a broken WLZ APK. The source APK is missing: "
            + ", ".join(missing)
            + ". The runtime-part ZIPs in the repository contain only DEX/native "
              "libraries; they do not include Minecraft assets or resources.arsc. "
              "Supply a complete, matching base Minecraft APK."
        )


def source_apk(payload):
    apks = sorted(payload.rglob("*.apk"))
    if apks:
        core = [p for p in apks if has_core(p)]
        if core:
            named = [p for p in core if p.name.lower() in {"base.apk", "minecraft.apk"}]
            source = max(named or core, key=lambda p: p.stat().st_size)
            validate_full_runtime(source)
            return source
        raise SystemExit(
            "Only split/config APKs found; no single APK contains "
            "the Minecraft native library and DEX. Supply a complete, matching base APK."
        )

    raise SystemExit(
        "No full source APK found. The current AndroidManifest.xml + dex.zip + "
        "arm64-v8a.zip + libminecraftpe.so bundle is incomplete; it has no assets/ "
        "or resources.arsc and produces a gray-screen/crashing game. Add a complete "
        "base APK to the build input. WLZ will not package the incomplete runtime."
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


def _uleb128(value):
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def _read_string_pool(xml):
    u16 = lambda p: struct.unpack_from("<H", xml, p)[0]
    u32 = lambda p: struct.unpack_from("<I", xml, p)[0]
    start = 8
    if u16(start) != 0x0001:
        raise SystemExit("AndroidManifest.xml has no string pool at expected offset")
    header_size = u16(start + 2)
    size = u32(start + 4)
    count = u32(start + 8)
    style_count = u32(start + 12)
    flags = u32(start + 16)
    strings_start = u32(start + 20)
    if style_count:
        raise SystemExit("Styled AndroidManifest string pools are not supported")
    strings = []
    base = start + strings_start
    for i in range(count):
        pos = base + u32(start + header_size + 4 * i)
        if flags & 0x100:
            first, pos = _read_uleb128(xml, pos)
            byte_len, pos = _read_uleb128(xml, pos)
            raw = xml[pos:pos + byte_len]
            strings.append(raw.decode("utf-8", "replace"))
        else:
            first = u16(pos)
            pos += 2
            if first & 0x8000:
                second = u16(pos)
                pos += 2
                units = ((first & 0x7FFF) << 16) | second
            else:
                units = first
            strings.append(xml[pos:pos + units * 2].decode("utf-16le", "replace"))
    return strings, size, flags, header_size


def _read_uleb128(buf, pos):
    value = 0
    shift = 0
    while True:
        byte = buf[pos]
        pos += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, pos
        shift += 7


def _build_string_pool(strings, flags):
    data = bytearray()
    offsets = []
    if flags & 0x100:
        for value in strings:
            encoded = value.encode("utf-8")
            offsets.append(len(data))
            data += _uleb128(len(value))
            data += _uleb128(len(encoded))
            data += encoded + b"\x00"
    else:
        for value in strings:
            encoded = value.encode("utf-16le")
            units = len(encoded) // 2
            offsets.append(len(data))
            if units > 0x7FFF:
                data += struct.pack("<HH", 0x8000 | (units >> 16), units & 0xFFFF)
            else:
                data += struct.pack("<H", units)
            data += encoded + b"\x00\x00"
    while len(data) % 4:
        data.append(0)
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    size = strings_start + len(data)
    chunk = bytearray(struct.pack(
        "<HHIIIIII", 0x0001, header_size, size, len(strings), 0,
        flags, strings_start, 0
    ))
    for offset in offsets:
        chunk += struct.pack("<I", offset)
    chunk += data
    return bytes(chunk)


def _xml_chunks(xml):
    chunks = []
    pos = 8
    while pos + 8 <= len(xml):
        typ, header_size, size = struct.unpack_from("<HHI", xml, pos)
        if size < 8 or pos + size > len(xml):
            raise SystemExit("Malformed AndroidManifest chunk")
        chunks.append((pos, typ, header_size, size))
        pos += size
    if pos != len(xml):
        raise SystemExit("AndroidManifest has trailing malformed bytes")
    return chunks


def _node_attr(xml, strings, node, wanted):
    start = node["start"]
    attr_start = struct.unpack_from("<H", xml, start + 24)[0]
    attr_size = struct.unpack_from("<H", xml, start + 26)[0]
    count = struct.unpack_from("<H", xml, start + 28)[0]
    attrs = []
    base = start + 16 + attr_start
    for i in range(count):
        p = base + i * attr_size
        name_idx = struct.unpack_from("<I", xml, p + 4)[0]
        raw_idx = struct.unpack_from("<I", xml, p + 8)[0]
        val_type = xml[p + 15]
        value_data = struct.unpack_from("<I", xml, p + 16)[0]
        name = strings[name_idx] if name_idx < len(strings) else ""
        if raw_idx != 0xFFFFFFFF and raw_idx < len(strings):
            value = strings[raw_idx]
        elif val_type == 3 and value_data < len(strings):
            value = strings[value_data]
        elif val_type == 0x12:
            value = bool(value_data)
        elif val_type == 1:
            value = "@%08x" % value_data
        else:
            value = value_data
        attrs.append((name, value, p, raw_idx, val_type, value_data))
    for item in attrs:
        if item[0] == wanted:
            return item
    return None


def _xml_nodes(xml, strings):
    nodes = []
    stack = []
    for pos, typ, header_size, size in _xml_chunks(xml):
        if typ == 0x0102:
            name_idx = struct.unpack_from("<I", xml, pos + 20)[0]
            name = strings[name_idx] if name_idx < len(strings) else ""
            node = {
                "name": name, "start": pos, "end_start": None, "end": None,
                "parent": stack[-1] if stack else None, "children": []
            }
            if stack:
                stack[-1]["children"].append(node)
            nodes.append(node)
            stack.append(node)
        elif typ == 0x0103:
            if not stack:
                raise SystemExit("Unbalanced AndroidManifest end-element")
            node = stack.pop()
            node["end_start"] = pos
            node["end"] = pos + size
    if stack:
        raise SystemExit("Unclosed AndroidManifest elements")
    return nodes


def _set_string_attribute(xml, node, strings, attr_name, value_index):
    start = node["start"]
    attr_start = struct.unpack_from("<H", xml, start + 24)[0]
    attr_size = struct.unpack_from("<H", xml, start + 26)[0]
    count = struct.unpack_from("<H", xml, start + 28)[0]
    base = start + 16 + attr_start
    for i in range(count):
        p = base + i * attr_size
        name_idx = struct.unpack_from("<I", xml, p + 4)[0]
        if name_idx >= len(strings) or strings[name_idx] != attr_name:
            continue
        struct.pack_into("<I", xml, p + 8, value_index)
        struct.pack_into("<H", xml, p + 12, 8)
        xml[p + 14] = 0
        xml[p + 15] = 3  # TYPE_STRING
        struct.pack_into("<I", xml, p + 16, value_index)
        return
    raise SystemExit("AndroidManifest attribute not found: " + attr_name)


def _start_element(strings, tag, attributes, android_ns):
    tag_idx = strings.index(tag)
    size = 36 + 20 * len(attributes)
    chunk = bytearray(struct.pack("<HHIII", 0x0102, 16, size, 0, 0xFFFFFFFF))
    chunk += struct.pack("<IIHHHHHH", 0xFFFFFFFF, tag_idx, 20, 20,
                         len(attributes), 0, 0, 0)
    for name, value, value_type, data in attributes:
        name_idx = strings.index(name)
        raw_idx = value if value_type == 3 else 0xFFFFFFFF
        ns_idx = android_ns
        chunk += struct.pack("<IIIHBBI", ns_idx, name_idx, raw_idx,
                             8, 0, value_type, data)
    return bytes(chunk)


def _end_element(strings, tag):
    return struct.pack("<HHIII", 0x0103, 16, 24, 0, 0xFFFFFFFF) + struct.pack(
        "<II", 0xFFFFFFFF, strings.index(tag)
    )


def _activity_chunks(strings, package_id):
    no_index = 0xFFFFFFFF
    android_ns = strings.index("http://schemas.android.com/apk/res/android")
    name = strings.index("name")
    exported = strings.index("exported")
    orientation = strings.index("screenOrientation")
    theme = strings.index("theme")
    intent_filter = strings.index("intent-filter")
    action = strings.index("action")
    category = strings.index("category")
    action_main = strings.index("android.intent.action.MAIN")
    category_launcher = strings.index("android.intent.category.LAUNCHER")

    def attr_string(attr_name, value):
        idx = strings.index(value)
        return (attr_name, idx, 3, idx)

    def attr_bool(attr_name, value):
        return (attr_name, value, 0x12, 1 if value else 0)

    def attr_int(attr_name, value):
        return (attr_name, no_index, 0x10, value)

    def attr_ref(attr_name, value):
        return (attr_name, no_index, 1, value)

    main_attrs = [
        attr_string("name", "com.wlz.client.MainActivity"),
        attr_bool("exported", True),
        attr_int("screenOrientation", 1),
        attr_ref("theme", 0x0103022E),
    ]
    editor_attrs = [
        attr_string("name", "com.wlz.client.WlzControlEditorActivity"),
        attr_bool("exported", False),
        attr_int("screenOrientation", 1),
        attr_ref("theme", 0x0103022E),
    ]

    main = bytearray(_start_element(strings, "activity", main_attrs, android_ns))
    main += _start_element(strings, "intent-filter", [], android_ns)
    main += _start_element(strings, "action", [attr_string("name", "android.intent.action.MAIN")], android_ns)
    main += _end_element(strings, "action")
    main += _start_element(strings, "category", [attr_string("name", "android.intent.category.LAUNCHER")], android_ns)
    main += _end_element(strings, "category")
    main += _end_element(strings, "intent-filter")
    main += _end_element(strings, "activity")

    editor = bytearray(_start_element(strings, "activity", editor_attrs, android_ns))
    editor += _end_element(strings, "activity")
    return bytes(main + editor)


def patch_manifest(base_manifest):
    # Preserve the original Apollon/Minecraft manifest and its entire provider,
    # permission, metadata and native-library bootstrap declarations. The former
    # build replaced it with the tiny helper manifest, silently dropping those
    # declarations and causing install/runtime failures.
    strings, old_pool_size, flags, pool_header_size = _read_string_pool(base_manifest)
    original_package = "com.mojang.minecraftpe"
    package_id = "com.wlzclient.launcher"
    if len(original_package) != len(package_id):
        raise SystemExit("WLZ package ID must stay the same length for stable pool refs")

    # Keep every original string index stable. Rewrite only the exact package
    # string and provider/permission authorities, never activity class names.
    authority_suffixes = {
        ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        ".fileprovider",
        ".firebaseinitprovider",
        ".playgamesinitprovider",
        ".androidx-startup",
    }
    for i, value in enumerate(strings):
        if value == original_package:
            strings[i] = package_id
        elif value.startswith(original_package + ".") and value[len(original_package):] in authority_suffixes:
            strings[i] = package_id + value[len(original_package):]

    def add_string(value):
        try:
            return strings.index(value)
        except ValueError:
            strings.append(value)
            return len(strings) - 1

    add_string(package_id)
    app_name_idx = add_string("com.wlz.client.WlzApplication")
    app_label_idx = add_string("WLZ Client")
    add_string("com.wlz.client.MainActivity")
    add_string("com.wlz.client.WlzControlEditorActivity")

    pool_start = 8
    pool_old_end = pool_start + old_pool_size
    old_pool = base_manifest[pool_start:pool_old_end]
    new_pool = _build_string_pool(strings, flags)
    xml = bytearray(base_manifest[:pool_start] + new_pool + base_manifest[pool_old_end:])
    struct.pack_into("<I", xml, 4, len(xml))

    nodes = _xml_nodes(xml, strings)
    root = next((n for n in nodes if n["name"] == "manifest" and n["parent"] is None), None)
    app = next((n for n in nodes if n["name"] == "application"), None)
    if root is None or app is None:
        raise SystemExit("Original manifest has no root or application node")
    _set_string_attribute(xml, root, strings, "package", strings.index(package_id))
    _set_string_attribute(xml, app, strings, "name", app_name_idx)
    _set_string_attribute(xml, app, strings, "label", app_label_idx)

    # Replace the original Minecraft MAIN/LAUNCHER filter with the WLZ launcher,
    # but keep every other intent filter (deep links, file imports, etc.).
    nodes = _xml_nodes(xml, strings)
    mc_activity = next(
        (n for n in nodes if n["name"] == "activity"
         and (_node_attr(xml, strings, n, "name") or (None,))[1] == "com.mojang.minecraftpe.MainActivity"),
        None,
    )
    if mc_activity is None:
        raise SystemExit("Original Minecraft MainActivity not found")
    delete_ranges = []
    for child in mc_activity["children"]:
        if child["name"] != "intent-filter":
            continue
        direct = {n["name"]: n for n in child["children"]}
        action_node = direct.get("action")
        category_node = direct.get("category")
        action_value = _node_attr(xml, strings, action_node, "name")[1] if action_node else None
        category_value = _node_attr(xml, strings, category_node, "name")[1] if category_node else None
        if action_value == "android.intent.action.MAIN" and category_value == "android.intent.category.LAUNCHER":
            delete_ranges.append((child["start"], child["end"]))
    for start, end in sorted(delete_ranges, reverse=True):
        del xml[start:end]
    struct.pack_into("<I", xml, 4, len(xml))

    nodes = _xml_nodes(xml, strings)
    app = next((n for n in nodes if n["name"] == "application"), None)
    if app is None or app["end_start"] is None:
        raise SystemExit("Could not locate application closing element")
    activity_bytes = _activity_chunks(strings, package_id)
    xml[app["end_start"]:app["end_start"]] = activity_bytes
    struct.pack_into("<I", xml, 4, len(xml))
    return bytes(xml)


def inject(helper, base, out):
    validate_full_runtime(base)

    base_entries = read_zip(base)
    helper_entries = read_zip(helper)
    original_manifest = base_entries.get("AndroidManifest.xml")
    if original_manifest is None:
        raise SystemExit("Source APK has no AndroidManifest.xml")
    merged_manifest = patch_manifest(original_manifest[1])

    out_entries = dict(base_entries)
    for name in list(out_entries):
        upper = name.upper()
        if upper.startswith("META-INF/") and (
            upper == "META-INF/MANIFEST.MF"
            or upper.endswith((".SF", ".RSA", ".DSA", ".EC"))
        ):
            del out_entries[name]

    # Use the patched source manifest, never the helper manifest. Keep the
    # source resources.arsc/res/assets intact and append only helper DEX/native.
    out_entries["AndroidManifest.xml"] = (original_manifest[0], merged_manifest)
    existing = set(out_entries)

    helper_dex = [
        n for n in helper_entries
        if re.fullmatch(r"classes[0-9]*\.dex", Path(n).name)
    ]
    for name in sorted(helper_dex, key=lambda x: (0 if Path(x).name == "classes.dex" else 1, x)):
        nums = []
        for n in existing:
            m = re.fullmatch(r"classes([0-9]*)\.dex", Path(n).name)
            if m:
                nums.append(1 if not m.group(1) else int(m.group(1)))
        dest = "classes.dex" if not nums else f"classes{max(nums) + 1}.dex"
        out_entries[dest] = helper_entries[name]
        existing.add(dest)

    for name, entry in helper_entries.items():
        if name.startswith("lib/arm64-v8a/") and name.endswith(".so") and name not in existing:
            out_entries[name] = entry
            existing.add(name)

    write_zip(out_entries, out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--wlz-apk", required=True)
    # Kept for workflow compatibility; manifest merging is now performed in Python.
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
