package com.wlz.client;

import android.app.Activity;
import android.os.Bundle;

public final class WlzApplication extends com.zihao_il.MinecraftApplication {
    private static final long NATIVE_ATTACH_DELAY_MS = 3500L;

    private final ActivityLifecycleCallbacks callbacks = new ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(Activity activity, Bundle state) {}

        @Override public void onActivityStarted(Activity activity) {}

        @Override public void onActivityResumed(final Activity activity) {
            if (!isMinecraft(activity)) return;

            // Let Minecraft's original Application/bootstrap finish first.
            // WLZ only attaches its HUD after the game Activity is actually resumed.
            activity.getWindow().getDecorView().postDelayed(new Runnable() {
                @Override public void run() {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        try { WlzInGameHud.attach(activity); } catch (Throwable ignored) {}
                    }
                }
            }, NATIVE_ATTACH_DELAY_MS);
        }

        @Override public void onActivityPaused(Activity activity) {}

        @Override public void onActivityStopped(Activity activity) {}

        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

        @Override public void onActivityDestroyed(Activity activity) {
            if (isMinecraft(activity)) {
                try { WlzInGameHud.detach(activity); } catch (Throwable ignored) {}
            }
        }
    };

    @Override
    public void onCreate() {
        // Critical: preserve the original Minecraft Application bootstrap.
        super.onCreate();

        WlzModuleManager.initialize(this);
        registerActivityLifecycleCallbacks(callbacks);
    }

    private static boolean isMinecraft(Activity activity) {
        return activity != null
                && "com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName());
    }
}
