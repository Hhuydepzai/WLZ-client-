package com.wlz.client;

public final class WlzRuntimeBridge {
    static {
        System.loadLibrary("wlzruntime");
    }

    private WlzRuntimeBridge() {}

    public static native boolean nativeInitialize();
    public static native boolean nativeIsLoaded();
    public static native void nativeSetModule(int index, boolean enabled);
    public static native boolean nativeGetModule(int index);
    public static native void nativeSetParam(int key, int value);
    public static native int nativeGetParam(int key);
}
