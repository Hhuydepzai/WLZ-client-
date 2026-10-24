package com.wlz.client;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * Runs inside the original Apollon/Minecraft process.  Its actual superclass
 * is the original Apollon V6.6 application class. The helper is compiled against
 * a compile-only stub for the actual PairIP application class in the source DEX.
 */
public final class WlzApplication extends com.pairip.application.Application {
    private static final long HUD_ATTACH_DELAY_MS = 1500L;

    private final ActivityLifecycleCallbacks callbacks = new ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(Activity activity, Bundle state) {}

        @Override public void onActivityResumed(final Activity activity) {
            if (!isMinecraft(activity) || activity.isFinishing()) return;
            activity.getWindow().getDecorView().postDelayed(new Runnable() {
                @Override public void run() {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        try { WlzInGameHud.attach(activity); } catch (Throwable ignored) {}
                    }
                }
            }, HUD_ATTACH_DELAY_MS);
        }

        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

        @Override public void onActivityDestroyed(Activity activity) {
            if (isMinecraft(activity)) {
                try { WlzInGameHud.detach(activity); } catch (Throwable ignored) {}
            }
        }
    };

    @Override public void onCreate() {
        // Preserve the original PairIP startup/bootstrap flow.
        super.onCreate();
        try {
            WlzModuleManager.initialize(this);
        } catch (Throwable error) {
            // WLZ modules are optional. A settings/native initialization issue must
            // not prevent Android from opening the launcher or the base game.
            Log.e("WLZStartup", "Optional WLZ module initialization failed", error);
        }

        // Keep the activity hook in whichever process creates Minecraft MainActivity.
        try {
            registerActivityLifecycleCallbacks(callbacks);
        } catch (Throwable error) {
            Log.e("WLZStartup", "Could not register activity callbacks", error);
        }
    }

    private static boolean isMinecraft(Activity activity) {
        return activity != null
                && "com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName());
    }
}
