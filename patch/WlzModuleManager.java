package com.wlz.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public final class WlzModuleManager {
    private static final String TAG = "WLZRuntime";
    private static final String PREFS = "wlz_settings";
    private static final int MODULE_COUNT = 9;
    private WlzModuleManager() {}

    public static boolean isModuleSupported(Context context, int index) {
        if (index == 8) return true;
        if (index < 0 || index >= MODULE_COUNT) return false;
        try {
            return WlzRuntimeBridge.nativeIsModuleSupported(index);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isModuleEnabled(Context context, int index) {
        if (index < 0 || index >= MODULE_COUNT) return false;
        return prefs(context).getBoolean("m_" + index, false);
    }

    public static void setModuleEnabled(Context context, int index, boolean enabled) {
        if (index < 0 || index >= MODULE_COUNT) return;
        if (!isModuleSupported(context, index)) {
            Log.w(TAG, "Ignoring unsupported module index=" + index);
            return;
        }
        prefs(context).edit().putBoolean("m_" + index, enabled).apply();
        try {
            WlzRuntimeBridge.nativeSetModule(index, enabled);
        } catch (Throwable e) {
            Log.w(TAG, "nativeSetModule unavailable for index=" + index, e);
        }
    }

    public static int getParam(Context context, int key, int fallback) {
        return prefs(context).getInt("param_" + key, fallback);
    }

    public static void setParam(Context context, int key, int value) {
        if (key >= 0) prefs(context).edit().putInt("param_" + key, value).apply();
        try {
            WlzRuntimeBridge.nativeSetParam(key, value);
        } catch (Throwable e) {
            Log.w(TAG, "nativeSetParam unavailable for key=" + key, e);
        }
    }

    public static void initialize(Context context) {
        try {
            WlzRuntimeBridge.nativeInitialize();
            // Push saved state even before Minecraft is loaded. The native
            // runtime keeps it pending and applies it as soon as signatures resolve.
            for (int i = 0; i < MODULE_COUNT; i++) {
                WlzRuntimeBridge.nativeSetModule(i, isModuleEnabled(context, i));
            }
        } catch (Throwable e) {
            Log.w(TAG, "native runtime initialization unavailable", e);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
