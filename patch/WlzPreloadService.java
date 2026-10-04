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

public final class WlzPreloadService extends Service {
    private static final String CHANNEL = "wlz_runtime";
    private WlzOverlayController overlay;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
        WlzModuleManager.initialize(this);

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        overlay = new WlzOverlayController(this);
        overlay.show();
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
                .setContentText("WLZ runtime + shortcut đang chạy")
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (overlay == null && Settings.canDrawOverlays(this)) {
            overlay = new WlzOverlayController(this);
            overlay.show();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (overlay != null) overlay.close();
        overlay = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
