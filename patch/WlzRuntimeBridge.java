package com.wlz.client;

import android.util.Log;

/** JNI bridge for the WLZ native runtime. Keep this class name in sync with JNI exports. */
public final class WlzRuntimeBridge {
    private static final String TAG = "WLZRuntimeBridge";
    private static volatile boolean loaded;

    private WlzRuntimeBridge() {}

    public static synchronized boolean ensureLoaded() {
        if (loaded) return true;
        try {
            System.loadLibrary("wlzruntime");
            loaded = true;
            Log.i(TAG, "Loaded libwlzruntime.so");
            return true;
        } catch (Throwable error) {
            loaded = false;
            Log.e(TAG, "Could not load libwlzruntime.so", error);
            return false;
        }
    }

    public static boolean isLoaded() { return loaded; }

    public static native boolean nativeInitialize();
    public static native boolean nativeIsLoaded();
    public static native boolean nativeIsMinecraftReady();
    public static native boolean nativeIsModuleSupported(int index);
    public static native int nativeGetCapabilities();
    public static native void nativeSetModule(int index, boolean enabled);
    public static native boolean nativeGetModule(int index);
    public static native void nativeSetParam(int key, int value);
    public static native int nativeGetParam(int key);
}
