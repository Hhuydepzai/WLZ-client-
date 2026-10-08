package com.wlz.client;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

{
    private static final long NATIVE_ATTACH_DELAY_MS = 3500L;

    private final ActivityLifecycleCallbacks callbacks = new ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(Activity activity, Bundle state) {}

        @Override public void onActivityStarted(Activity activity) {}

        @Override public void onActivityResumed(final Activity activity) {
            if (!isMinecraft(activity)) return;
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
        if (isMinecraftProcess()) {
            // Only the Minecraft process is allowed to run the original
            // MinecraftApplication bootstrap.
            super.onCreate();
            WlzModuleManager.initialize(this);
            registerActivityLifecycleCallbacks(callbacks);
        } else {
            // Launcher process must stay lightweight and must not bootstrap
            // the native Minecraft runtime.
            WlzModuleManager.initialize(this);
        }
    }

    private boolean isMinecraftProcess() {
        String process = android.app.Application.getProcessName();
        return process != null && process.endsWith(":mc");
    }

    private static boolean isMinecraft(Activity activity) {
        return activity != null
                && "com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName());
    }
}
