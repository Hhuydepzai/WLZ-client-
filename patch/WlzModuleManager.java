package com.wlz.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public final class WlzModuleManager {
    private static final String TAG = "WLZRuntime";
    private static final String PREFS = "wlz_settings";
    private static final int MODULE_COUNT = 9;
    private static volatile boolean nativeStarted = false;

    private WlzModuleManager() {}

    public static boolean isModuleSupported(Context context, int index) {
        if (index == 8) return true;
        if (index < 0 || index >= MODULE_COUNT) return false;

        // Do not load the native runtime from the launcher process. Minecraft's
        // native library must be fully started first, otherwise signature
        // scanning/hooking can crash the process during startup.
        if (!nativeStarted) return false;

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
        if (nativeStarted) {
            try {
                WlzRuntimeBridge.nativeSetModule(index, enabled);
            } catch (Throwable e) {
                Log.w(TAG, "nativeSetModule unavailable for index=" + index, e);
            }
        }
    }

    public static int getParam(Context context, int key, int fallback) {
        return prefs(context).getInt("param_" + key, fallback);
    }

    public static void setParam(Context context, int key, int value) {
        if (key >= 0) prefs(context).edit().putInt("param_" + key, value).apply();
        if (nativeStarted) {
            try {
                WlzRuntimeBridge.nativeSetParam(key, value);
            } catch (Throwable e) {
                Log.w(TAG, "nativeSetParam unavailable for key=" + key, e);
            }
        }
    }

    public static void initialize(Context context) {
        // Safe launcher-side initialization only. The native runtime is
        // deliberately started after Minecraft's Activity is resumed.
        Log.d(TAG, "WLZ module manager initialized in safe Java-only mode");
    }

    public static void initializeNative(Context context) {
        if (nativeStarted) return;

        try {
            WlzRuntimeBridge.nativeInitialize();
            nativeStarted = true;

            // Do not re-apply persisted native toggles automatically. Native
            // hooks are only installed after an explicit user action, which keeps
            // the Minecraft startup path free of invasive patches.
            Log.i(TAG, "WLZ native runtime attached after Minecraft resume");
        } catch (Throwable e) {
            nativeStarted = false;
            Log.w(TAG, "Native runtime attachment failed; continuing without native modules", e);
        }
    }

    public static boolean isNativeStarted() {
        return nativeStarted;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
