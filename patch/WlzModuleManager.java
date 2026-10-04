package com.wlz.client;

import android.content.Context;
import android.content.SharedPreferences;

public final class WlzModuleManager {
    private static final String PREFS = "wlz_settings";
    private static final int MODULE_COUNT = 9;
    private WlzModuleManager() {}

    public static boolean isModuleEnabled(Context context, int index) {
        if (index < 0 || index >= MODULE_COUNT) return false;
        return prefs(context).getBoolean("m_" + index, false);
    }

    public static void setModuleEnabled(Context context, int index, boolean enabled) {
        if (index < 0 || index >= MODULE_COUNT) return;
        prefs(context).edit().putBoolean("m_" + index, enabled).apply();
        try {
            WlzRuntimeBridge.nativeSetModule(index, enabled);
        } catch (Throwable ignored) {
            // Native runtime is optional until the game-side runtime is available.
        }
    }

    public static int getParam(Context context, int key, int fallback) {
        return prefs(context).getInt("param_" + key, fallback);
    }

    public static void setParam(int key, int value) {
        try {
            WlzRuntimeBridge.nativeSetParam(key, value);
        } catch (Throwable ignored) {
        }
    }

    public static void initialize(Context context) {
        try {
            WlzRuntimeBridge.nativeInitialize();
            for (int i = 0; i < MODULE_COUNT; i++) {
                boolean enabled = isModuleEnabled(context, i);
                WlzRuntimeBridge.nativeSetModule(i, enabled);
            }
        } catch (Throwable ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
