package com.wlz.client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

public final class WlzPreloadService extends Service {
    public static final String ACTION_START_CLIENT = "com.wlz.client.action.START_CLIENT";
    public static final String ACTION_SHOW_OVERLAY = "com.wlz.client.action.SHOW_OVERLAY";

    private static final String CHANNEL = "wlz_runtime";
    private static final long OVERLAY_DELAY_MS = 2400L;

    private WlzOverlayController overlay;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
        WlzModuleManager.initialize(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW_OVERLAY : intent.getAction();

        if (ACTION_START_CLIENT.equals(action)) {
            launchMinecraftThenOverlay();
            return START_STICKY;
        }

        showOverlay();
        return START_STICKY;
    }

    private void launchMinecraftThenOverlay() {
        Intent minecraft = getPackageManager().getLaunchIntentForPackage("com.mojang.minecraftpe");
        if (minecraft == null) {
            Log.w("WLZRuntime", "Minecraft package not installed");
            stopSelf();
            return;
        }

        try {
            minecraft.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(minecraft);
        } catch (Throwable e) {
            Log.e("WLZRuntime", "Minecraft launch failed", e);
            stopSelf();
            return;
        }

        handler.removeCallbacksAndMessages(null);
        if (Settings.canDrawOverlays(this)) {
            handler.postDelayed(this::showOverlay, OVERLAY_DELAY_MS);
        } else {
            Log.i("WLZRuntime", "Overlay permission missing; Minecraft launched without floating shortcut");
            handler.postDelayed(this::stopSelf, OVERLAY_DELAY_MS);
        }
    }

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        if (overlay == null) {
            overlay = new WlzOverlayController(this);
            overlay.show();
        }
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "WLZ Runtime", NotificationManager.IMPORTANCE_LOW));
        }

        Intent launch = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 3, launch, piFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("WLZ Client")
                .setContentText("WLZ floating runtime đang chạy")
                .setContentIntent(pi)
                .setOngoing(true);

        Notification n = builder.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1606, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1606, n);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (overlay != null) overlay.close();
        overlay = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
