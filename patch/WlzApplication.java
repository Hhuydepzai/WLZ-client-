package com.wlz.client;

import android.app.Application;
import android.util.Log;

/**
 * WLZ is a standalone launcher. It deliberately does not subclass Apollon's
 * PairIP Application or start Minecraft's native runtime inside this process.
 */
public final class WlzApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Log.i("WLZLauncher", "Standalone WLZ launcher started");
    }
}
