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
        if (index < 0 || index >= MODULE_COUNT || !nativeStarted) return false;
        try { return WlzRuntimeBridge.nativeIsModuleSupported(index); }
        catch (Throwable e) { return false; }
    }

    public static boolean isModuleEnabled(Context context, int index) {
        if (index < 0 || index >= MODULE_COUNT) return false;
        return prefs(context).getBoolean("m_" + index, false);
    }

    public static void setModuleEnabled(Context context, int index, boolean enabled) {
        if (index < 0 || index >= MODULE_COUNT) return;

        if (index != 8 && !nativeStarted) initializeNative(context);

        if (index != 8 && !isModuleSupported(context, index)) {
            Log.w(TAG, "Ignoring unsupported module index=" + index);
            return;
        }

        prefs(context).edit().putBoolean("m_" + index, enabled).apply();

        if (nativeStarted) {
            try { WlzRuntimeBridge.nativeSetModule(index, enabled); }
            catch (Throwable e) { Log.w(TAG, "nativeSetModule failed for index=" + index, e); }
        }
    }

    public static int getParam(Context context, int key, int fallback) {
        return prefs(context).getInt("param_" + key, fallback);
    }

    public static void setParam(Context context, int key, int value) {
        if (key >= 0) prefs(context).edit().putInt("param_" + key, value).apply();
        if (nativeStarted) {
            try { WlzRuntimeBridge.nativeSetParam(key, value); }
            catch (Throwable e) { Log.w(TAG, "nativeSetParam failed for key=" + key, e); }
        }
    }

    public static void initialize(Context context) {
        Log.d(TAG, "WLZ Java layer initialized; native runtime deferred");
    }

    public static void initializeNative(Context context) {
        if (nativeStarted) return;
        if (!WlzRuntimeBridge.ensureLoaded()) {
            Log.w(TAG, "Native library failed to load; continuing without native modules");
            return;
        }
        try {
            if (WlzRuntimeBridge.nativeInitialize()) nativeStarted = true;
            else Log.w(TAG, "nativeInitialize returned false");
        } catch (Throwable e) {
            nativeStarted = false;
            Log.w(TAG, "WLZ native runtime start failed", e);
        }
    }

    public static boolean isNativeStarted() { return nativeStarted; }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
