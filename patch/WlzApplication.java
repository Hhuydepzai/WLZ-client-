package com.wlz.client;

import android.app.Activity;
import android.os.Bundle;

/**
 * Runs inside the original Apollon/Minecraft process.  Its actual superclass
 * is provided by the base APK, so the helper is compiled against a compile-only
 * stub and never bundles a fake replacement class.
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
        // Preserve the base APK's PairIP bootstrap and its normal license/startup flow.
        super.onCreate();
        WlzModuleManager.initialize(this);
        // Do not gate this on a :mc process name: the supplied V6.6 base APK
        // launches MainActivity in the default process.
        registerActivityLifecycleCallbacks(callbacks);
    }

    private static boolean isMinecraft(Activity activity) {
        return activity != null
                && "com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName());
    }
}
