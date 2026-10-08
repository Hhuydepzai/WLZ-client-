package com.wlz.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WlzKeyMapper {
    public static final String[] ACTIONS = {
            "Zoom", "FreeLook", "Ném đồ", "Unlock FPS", "Fullbright",
            "Hitbox", "AutoSprint", "Snaplook", "FPS Counter"
    };

    private static final int[] DEFAULT_KEYS = {
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_F3
    };

    private static final String KEYMAP_PREFS = "wlz_keymap";
    private static final String SETTINGS_PREFS = "wlz_settings";

    private WlzKeyMapper() {}

    public static int getKey(Context context, int action) {
        if (action < 0 || action >= ACTIONS.length) return KeyEvent.KEYCODE_UNKNOWN;
        return prefs(context).getInt("key_" + action, DEFAULT_KEYS[action]);
    }

    public static void setKey(Context context, int action, int keyCode) {
        if (action < 0 || action >= ACTIONS.length) return;
        prefs(context).edit().putInt("key_" + action, keyCode).apply();
        setHotkeysEnabled(context, true);
    }

    public static boolean areHotkeysEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean("hotkeys_enabled", false);
    }

    public static void setHotkeysEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("hotkeys_enabled", enabled).apply();
        try { WlzInGameHud.refreshAllHotkeys(); } catch (Throwable ignored) {}
    }

    public static String keyName(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return "—";
        String s = KeyEvent.keyCodeToString(keyCode);
        if (s.startsWith("KEYCODE_")) s = s.substring(8);
        return s;
    }

    public static boolean trigger(Context context, int keyCode) {
        if (!areHotkeysEnabled(context)) return false;

        for (int i = 0; i < ACTIONS.length; i++) {
            if (getKey(context, i) == keyCode) {
                boolean enabled = WlzModuleManager.isModuleEnabled(context, i);
                WlzModuleManager.setModuleEnabled(context, i, !enabled);
                return true;
            }
        }
        return false;
    }

    public static Map<String, Integer> snapshot(Context context) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < ACTIONS.length; i++) {
            out.put(ACTIONS[i], getKey(context, i));
        }
        return out;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(KEYMAP_PREFS, Context.MODE_PRIVATE);
    }
}
