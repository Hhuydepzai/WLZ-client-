package com.wlz.client;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class WlzBootstrapProvider extends ContentProvider {
    private static final long HUD_ATTACH_DELAY_MS = 1500L;

    @Override public boolean onCreate() {
        if (getContext() == null) return false;
        final Application app = (Application) getContext().getApplicationContext();
        WlzModuleManager.initialize(app);

        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
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
                if (isMinecraft(activity)) WlzInGameHud.detach(activity);
            }
        });
        return true;
    }

    private static boolean isMinecraft(Activity activity) {
        return activity != null
                && "com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName());
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
