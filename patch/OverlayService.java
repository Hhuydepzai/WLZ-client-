package com.wlz.client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class OverlayService extends Service {
    private static final int PANEL = Color.rgb(20, 21, 26);
    private static final int ROW = Color.rgb(26, 27, 33);
    private static final int STROKE = Color.rgb(53, 55, 63);
    private static final int ORANGE = Color.rgb(246, 115, 20);
    private static final int ORANGE_DARK = Color.rgb(83, 38, 11);
    private static final int TEXT = Color.rgb(245, 245, 247);
    private static final int MUTED = Color.rgb(157, 160, 169);
    private static final String CH = "wlz_overlay";
    private static final String[] N = {"Zoom","FreeLook","Ném đồ","Unlock FPS","Fullbright","Hitbox","AutoSprint","Snaplook","FPS Counter"};

    private WindowManager wm;
    private WindowManager.LayoutParams bubbleLp, panelLp;
    private TextView bubble;
    private LinearLayout panel;
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("wlz_settings", MODE_PRIVATE);
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        startForegroundCompat();
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        createBubble();
        createPanel();
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(new NotificationChannel(CH, "WLZ Overlay", NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("WLZ Client")
                .setContentText("In-game controls đang chạy")
                .setContentIntent(pi)
                .setOngoing(true);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1104, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1104, n);
    }

    private void createBubble() {
        bubble = new TextView(this);
        bubble.setText("W");
        bubble.setTextSize(16);
        bubble.setTextColor(TEXT);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bubble.setBackground(circle(ORANGE, 2));
        bubble.setElevation(dp(10));
        bubble.setContentDescription("WLZ overlay");

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = bubbleLp.x; startY = bubbleLp.y; moved = false; return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    int nx = startX + (int)(e.getRawX() - downX);
                    int ny = startY + (int)(e.getRawY() - downY);
                    if (Math.abs(nx - startX) + Math.abs(ny - startY) > dp(8)) moved = true;
                    bubbleLp.x = Math.max(0, nx);
                    bubbleLp.y = Math.max(0, ny);
                    try { wm.updateViewLayout(bubble, bubbleLp); } catch (Throwable ignored) {}
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (!moved) togglePanel();
                    return true;
                }
                return false;
            }
        });

        bubbleLp = overlayLp(54, 54, 18, 230, false);
        add(bubble, bubbleLp);
    }

    private void createPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(11), dp(11), dp(11), dp(11));
        panel.setBackground(round(PANEL, ORANGE, 1, 18));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("WLZ", 18, ORANGE, true), lp(0, -2, 1));
        TextView sub = text("IN-GAME", 9, MUTED, true);
        sub.setPadding(dp(8), dp(5), dp(8), dp(5));
        sub.setBackground(round(ORANGE_DARK, STROKE, 1, 10));
        head.addView(sub, lp(-2, -2, 0));
        Button close = button("×");
        close.setOnClickListener(v -> hidePanel());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(42), dp(40));
        cp.leftMargin = dp(6);
        head.addView(close, cp);
        panel.addView(head);

        panel.addView(text("Chạm W để mở menu • Kéo W để đổi vị trí", 9, MUTED, false), top(3));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < N.length; i++) list.addView(moduleRow(i));
        list.addView(fixLagRow(), top(4));

        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(390)));

        Button launcher = button("MỞ LAUNCHER WLZ");
        launcher.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        });
        panel.addView(launcher, top(7));

        panelLp = overlayLp(300, 470, 12, 300, true);
        panel.setVisibility(View.GONE);
        add(panel, panelLp);
    }

    private View moduleRow(int id) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), 0, dp(6), 0);
        row.setBackground(round(ROW, STROKE, 1, 12));

        TextView label = text(N[id], 11, TEXT, true);
        row.addView(label, lp(0, dp(44), 1));

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean("m_" + id, false));
        sw.setOnCheckedChangeListener((CompoundButton b, boolean enabled) -> {
            prefs.edit().putBoolean("m_" + id, enabled).apply();
            try { RuntimeBridge.nativeSetModule(id, enabled); } catch (Throwable ignored) {}
        });
        row.addView(sw, lp(-2, dp(44), 0));

        LinearLayout.LayoutParams rp = lp(-1, dp(46), 0);
        rp.bottomMargin = dp(5);
        row.setLayoutParams(rp);
        return row;
    }

    private View fixLagRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(9), dp(7), dp(7), dp(7));
        wrap.setBackground(round(ROW, ORANGE, 1, 13));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("FIX LAG", 12, TEXT, true), lp(0, -2, 1));
        head.addView(text("3 LEVEL", 9, ORANGE, true), lp(-2, -2, 0));
        wrap.addView(head);

        LinearLayout levels = new LinearLayout(this);
        int current = prefs.getInt("lag_profile", 1);
        String[] labels = {"NHẸ", "MẠNH", "SIÊU"};
        for (int i = 1; i <= 3; i++) {
            final int level = i;
            Button b = button(labels[i - 1]);
            if (current == level) b.setBackground(round(ORANGE, ORANGE, 1, 10));
            b.setOnClickListener(v -> applyFix(level));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 1) p.leftMargin = dp(5);
            levels.addView(b, p);
        }
        wrap.addView(levels, top(5));
        return wrap;
    }

    private void applyFix(int level) {
        level = Math.max(1, Math.min(3, level));
        boolean removeEffects = prefs.getBoolean("fix_remove_effects", level >= 2);
        int colors = level >= 3 ? 3 : 4;
        prefs.edit().putInt("lag_profile", level)
                .putBoolean("fix_remove_effects", removeEffects)
                .putInt("texture_colors", colors)
                .apply();
        try { RuntimeBridge.nativeSetModule(8, true); } catch (Throwable ignored) {}
        try { RuntimeBridge.nativeSetParam(3, level); } catch (Throwable ignored) {}
        try { RuntimeBridge.nativeSetParam(4, removeEffects ? 1 : 0); } catch (Throwable ignored) {}
        try { RuntimeBridge.nativeSetParam(5, colors); } catch (Throwable ignored) {}
        hidePanel();
    }

    private void togglePanel() {
        if (panel.getVisibility() == View.VISIBLE) { hidePanel(); return; }
        panelLp.x = Math.max(8, bubbleLp.x - dp(8));
        panelLp.y = Math.max(8, bubbleLp.y + dp(60));
        panel.setVisibility(View.VISIBLE);
        try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
    }

    private void hidePanel() { if (panel != null) panel.setVisibility(View.GONE); }

    private WindowManager.LayoutParams overlayLp(int w, int h, int x, int y, boolean focusable) {
        int flags = focusable
                ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(w), dp(h), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(x); lp.y = dp(y);
        return lp;
    }

    private void add(View v, WindowManager.LayoutParams lp) {
        try { wm.addView(v, lp); } catch (Throwable ignored) {}
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(TEXT); b.setTextSize(10); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(7), 0, dp(7), 0);
        b.setBackground(round(Color.rgb(37, 38, 44), STROKE, 1, 10));
        return b;
    }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(width), stroke); return d;
    }

    private GradientDrawable circle(int fill, int width) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL); d.setColor(fill); d.setStroke(dp(width), Color.WHITE); return d;
    }

    private LinearLayout.LayoutParams lp(int width, int height) { return lp(width, height, 0); }
    private LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }
    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams p = lp(-1, -2); p.topMargin = dp(margin); return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }

    @Override public void onDestroy() {
        try { if (bubble != null) wm.removeView(bubble); } catch (Throwable ignored) {}
        try { if (panel != null) wm.removeView(panel); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
