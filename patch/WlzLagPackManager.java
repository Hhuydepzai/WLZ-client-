package com.wlz.client;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs WLZ resource packs from helper APK assets and activates the selected
 * Bedrock global resource stack before MainActivity is launched.
 */
public final class WlzLagPackManager {
    private static final String TAG = "WLZLagPacks";
    private static final int PACK_REVISION = 1;

    private static final Pack COMMON_A = new Pack(
            "common_a.mcpack", "wlz_fixlag_common_a", "WLZ Fix Lag - Common A",
            "b8b4fc93-2a7a-5e9c-9e76-4d620e2d32e1",
            "72e3c504-7c2f-52b8-a2a4-2cde8c6cebb1", "noanimation");
    private static final Pack COMMON_B = new Pack(
            "common_b.mcpack", "wlz_fixlag_common_b", "WLZ Fix Lag - Common B",
            "e4ea3b8f-83bc-51b8-9866-3f9fb9fae9b7",
            "ba31b06d-68b6-52c8-8da4-40eece0a6691", "lite", "turbo");
    private static final Pack FIXLAG2 = new Pack(
            "fixlag2.mcpack", "wlz_fixlag_2", "WLZ Fix Lag 2",
            "1b69366c-3a89-5f25-90b4-19f3e30b5d63",
            "3120a1c8-1f2a-5bf5-85f5-9e0a8b5e9a31", null);
    private static final Pack FIXLAG3 = new Pack(
            "fixlag3.mcpack", "wlz_fixlag_3", "WLZ Fix Lag 3",
            "b4f92be6-7541-5b0e-8e2f-faa3fe8f2173",
            "61272814-6041-5c11-9ad0-c608dd3409c2", null);

    private static final Set<String> WLZ_IDS = new HashSet<String>();
    static {
        WLZ_IDS.add(COMMON_A.packUuid);
        WLZ_IDS.add(COMMON_B.packUuid);
        WLZ_IDS.add(FIXLAG2.packUuid);
        WLZ_IDS.add(FIXLAG3.packUuid);
    }

    private WlzLagPackManager() {}

    /**
     * True when all four packs are either embedded by the build or imported once
     * through the WLZ launcher. No manual Global Resources installation is needed.
     */
    public static boolean isBundled(Context context) {
        try {
            Set<String> names = new HashSet<String>();
            String[] embedded = context.getAssets().list("wlz_packs");
            if (embedded != null) for (String name : embedded) names.add(name);
            if (names.contains(COMMON_A.assetName) && names.contains(COMMON_B.assetName)
                    && names.contains(FIXLAG2.assetName) && names.contains(FIXLAG3.assetName)) return true;
        } catch (Throwable ignored) {}

        File source = new File(context.getFilesDir(), "wlz_pack_sources");
        for (Pack pack : ALL_PACKS) {
            File file = new File(source, pack.assetName);
            if (!file.isFile() || file.length() < 32) return false;
        }
        return true;
    }

    private static final Pack[] ALL_PACKS = {COMMON_A, COMMON_B, FIXLAG2, FIXLAG3};

    /** Copy a chosen .mcpack into private storage; its pack UUID/manifest are normalized at install time. */
    public static void importPack(Context context, String canonicalName, InputStream input) throws IOException {
        Pack pack = null;
        for (Pack candidate : ALL_PACKS) {
            if (candidate.assetName.equals(canonicalName)) { pack = candidate; break; }
        }
        if (pack == null) throw new IOException("Unknown WLZ pack name");
        if (input == null) throw new IOException("No data stream for " + canonicalName);

        File root = new File(context.getFilesDir(), "wlz_pack_sources");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create pack storage");
        File target = new File(root, canonicalName);
        File temp = new File(root, canonicalName + ".tmp");
        try (InputStream source = new BufferedInputStream(input);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
            byte[] buffer = new byte[32768];
            int n;
            while ((n = source.read(buffer)) != -1) out.write(buffer, 0, n);
        } catch (Throwable error) {
            temp.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Could not import " + canonicalName, error);
        }

        boolean hasEntry = false;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(temp)))) {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                hasEntry = true;
                while (zip.read(buffer) != -1) { /* consume to validate the archive stream */ }
                zip.closeEntry();
            }
        } catch (Throwable error) {
            temp.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Invalid mcpack archive: " + canonicalName, error);
        }
        if (!hasEntry || temp.length() < 32) {
            temp.delete();
            throw new IOException("Empty or invalid mcpack: " + canonicalName);
        }
        if (target.exists() && !target.delete()) { temp.delete(); throw new IOException("Cannot replace pack"); }
        if (!temp.renameTo(target)) { temp.delete(); throw new IOException("Cannot save pack"); }
        Log.i(TAG, "Imported " + canonicalName + " (" + target.length() + " bytes)");
    }

    public static int getSelectedProfile(Context context) {
        int value = context.getSharedPreferences("wlz_settings", Context.MODE_PRIVATE)
                .getInt("lag_profile", 1);
        return value >= 1 && value <= 3 ? value : 1;
    }

    /**
     * Returns false if the uploaded packs were not embedded. Does not silently
     * save a profile that cannot be applied.
     */
    public static boolean applyPreset(Context context, int profile) {
        if (profile < 1 || profile > 3 || !isBundled(context)) return false;
        try {
            File root = storageRoot(context);
            File packRoot = new File(root, "resource_packs");
            if (!packRoot.exists() && !packRoot.mkdirs()) {
                throw new IOException("Cannot create " + packRoot);
            }

            installPack(context, packRoot, COMMON_A);
            installPack(context, packRoot, COMMON_B);
            if (profile == 2) installPack(context, packRoot, FIXLAG2);
            if (profile == 3) installPack(context, packRoot, FIXLAG3);

            writeStack(new File(root, "global_resource_packs.json"), profile);
            context.getSharedPreferences("wlz_settings", Context.MODE_PRIVATE)
                    .edit().putInt("lag_profile", profile).apply();
            Log.i(TAG, "Applied WLZ Fix Lag profile " + profile);
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Could not apply Fix Lag profile " + profile, error);
            return false;
        }
    }

    private static InputStream openPackSource(Context context, Pack pack) throws IOException {
        try {
            return new BufferedInputStream(context.getAssets().open("wlz_packs/" + pack.assetName));
        } catch (IOException assetMissing) {
            File source = new File(new File(context.getFilesDir(), "wlz_pack_sources"), pack.assetName);
            if (!source.isFile() || source.length() < 32) throw new IOException("Missing Fix Lag pack: " + pack.assetName, assetMissing);
            return new BufferedInputStream(new FileInputStream(source));
        }
    }

    private static File storageRoot(Context context) throws IOException {
        File external = context.getExternalFilesDir(null);
        File base = external != null ? external : context.getFilesDir();
        File root = new File(base, "games/com.mojang");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create " + root);
        return root;
    }

    private static void installPack(Context context, File packRoot, Pack pack)
            throws IOException, JSONException {
        File target = new File(packRoot, pack.folderName);
        File manifest = new File(target, "manifest.json");
        File marker = new File(target, ".wlz_pack_revision");
        if (manifest.isFile() && marker.isFile()) {
            try (InputStream in = new FileInputStream(marker)) {
                byte[] b = new byte[16];
                int n = in.read(b);
                if (n > 0 && new String(b, 0, n, StandardCharsets.UTF_8).trim()
                        .equals(Integer.toString(PACK_REVISION))) return;
            }
        }

        deleteTree(target);
        if (!target.mkdirs()) throw new IOException("Cannot create pack folder " + target);
        try (ZipInputStream zip = new ZipInputStream(openPackSource(context, pack))) {
            ZipEntry entry;
            byte[] buffer = new byte[32768];
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("fixlag3/")) entryName = entryName.substring(8);
                if (entryName.isEmpty() || entryName.equals("manifest.json")) {
                    zip.closeEntry();
                    continue;
                }
                File out = new File(target, entryName);
                String parent = target.getCanonicalPath() + File.separator;
                if (!out.getCanonicalPath().startsWith(parent)) {
                    throw new IOException("Unsafe pack entry: " + entryName);
                }
                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("Cannot create " + out);
                } else {
                    File outParent = out.getParentFile();
                    if (outParent != null && !outParent.exists() && !outParent.mkdirs()) {
                        throw new IOException("Cannot create " + outParent);
                    }
                    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) stream.write(buffer, 0, n);
                    }
                }
                zip.closeEntry();
            }
        }

        JSONObject header = new JSONObject();
        header.put("name", pack.displayName);
        header.put("description", "Installed and managed by WLZ Client");
        header.put("uuid", pack.packUuid);
        header.put("version", new JSONArray("[1,0,0]"));
        header.put("min_engine_version", new JSONArray("[1,21,0]"));

        JSONObject module = new JSONObject();
        module.put("type", "resources");
        module.put("uuid", pack.moduleUuid);
        module.put("version", new JSONArray("[1,0,0]"));

        JSONObject root = new JSONObject();
        root.put("format_version", 2);
        root.put("header", header);
        root.put("modules", new JSONArray().put(module));
        if (pack.subpackNames.length > 0) {
            JSONArray subpacks = new JSONArray();
            for (String subpackName : pack.subpackNames) {
                JSONObject subpack = new JSONObject();
                subpack.put("folder_name", subpackName);
                subpack.put("name", subpackName.equals("turbo") ? "Turbo"
                        : (subpackName.equals("noanimation") ? "No Animation" : "Lite"));
                subpack.put("memory_tier", subpackName.equals("turbo") ? 1 : 0);
                subpacks.put(subpack);
            }
            root.put("subpacks", subpacks);
        }
        try (FileOutputStream out = new FileOutputStream(manifest)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        try (FileOutputStream out = new FileOutputStream(marker)) {
            out.write(Integer.toString(PACK_REVISION).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeStack(File file, int profile) throws IOException, JSONException {
        JSONArray existing = new JSONArray();
        if (file.isFile()) {
            byte[] data;
            try (FileInputStream in = new FileInputStream(file);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192];
                int n;
                while ((n = in.read(bytes)) != -1) buffer.write(bytes, 0, n);
                data = buffer.toByteArray();
            }
            String text = new String(data, StandardCharsets.UTF_8).trim();
            if (!text.isEmpty()) existing = new JSONArray(text);
        }

        JSONArray out = new JSONArray();
        // First entry wins when the same file is present in multiple packs.
        if (profile == 2) out.put(FIXLAG2.stackEntry());
        if (profile == 3) out.put(FIXLAG3.stackEntry());
        out.put(COMMON_B.stackEntry());
        out.put(COMMON_A.stackEntry());
        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item != null && !WLZ_IDS.contains(item.optString("pack_id", ""))) out.put(item);
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        File tmp = new File(parent, file.getName() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(tmp)) {
            stream.write(out.toString(2).getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
        if (file.exists() && !file.delete()) throw new IOException("Cannot replace " + file);
        if (!tmp.renameTo(file)) throw new IOException("Cannot replace " + file);
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("Cannot delete " + file);
    }

    private static final class Pack {
        final String assetName, folderName, displayName, packUuid, moduleUuid;
        final String[] subpackNames;
        Pack(String assetName, String folderName, String displayName,
             String packUuid, String moduleUuid, String... subpackNames) {
            this.assetName = assetName;
            this.folderName = folderName;
            this.displayName = displayName;
            this.packUuid = packUuid;
            this.moduleUuid = moduleUuid;
            this.subpackNames = subpackNames == null ? new String[0] : subpackNames;
        }

        JSONObject stackEntry() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("pack_id", packUuid);
            object.put("version", new JSONArray("[1,0,0]"));
            if (subpackNames.length > 0) object.put("subpack", subpackNames[0]);
            return object;
        }
    }
}
