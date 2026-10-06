package com.wlz.client;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public final class WlzApplication extends Application {
    private final ActivityLifecycleCallbacks callbacks = new ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(Activity activity, Bundle state) {}

        @Override public void onActivityStarted(Activity activity) {}

        @Override public void onActivityResumed(Activity activity) {
            if (isMinecraft(activity)) {
                WlzInGameHud.attach(activity);
            }
        }

        @Override public void onActivityPaused(Activity activity) {}

        @Override public void onActivityStopped(Activity activity) {}

        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

        @Override public void onActivityDestroyed(Activity activity) {
            if (isMinecraft(activity)) {
                WlzInGameHud.detach(activity);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        WlzModuleManager.initialize(this);
        registerActivityLifecycleCallbacks(callbacks);
    }

    private static boolean isMinecraft(Activity activity) {
        return activity != null
                && "com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName());
    }
}
