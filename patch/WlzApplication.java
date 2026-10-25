package com.wlz.client;

import android.app.Application;
import android.util.Log;

/**
 * Standalone WLZ launcher application.
 *
 * Minecraft is a separate installed Android app in this mode. Do not register
 * Activity callbacks or attach the old in-process HUD here: Android isolates
 * the two application processes, and pretending this process can hook the
 * external game was a source of misleading UI and startup failures.
 */
public final class WlzApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        try {
            WlzModuleManager.initialize(this);
            Log.i("WLZStartup", "Standalone launcher ready; Minecraft will be opened as an external app.");
        } catch (Throwable error) {
            // A launcher must still open even if an optional WLZ module is unavailable.
            Log.e("WLZStartup", "Optional WLZ module initialization failed", error);
        }
    }
}
