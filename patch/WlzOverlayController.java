package com.wlz.client;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class WlzOverlayController {
    private static final int PANEL = Color.rgb(14, 17, 22);
    private static final int ROW = Color.rgb(20, 24, 30);
    private static final int STROKE = Color.rgb(52, 61, 72);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int ORANGE_DARK = Color.rgb(78, 34, 4);
    private static final int TEXT = Color.rgb(241, 245, 249);
    private static final int MUTED = Color.rgb(145, 154, 166);
    private static final String[] N = {
            "Zoom", "FreeLook", "Ném đồ", "Unlock FPS", "Fullbright",
            "Hitbox", "AutoSprint", "Snaplook", "FPS Counter"
    };

    private final Context context;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private WindowManager.LayoutParams bubbleLp;
    private WindowManager.LayoutParams panelLp;
    private ImageView bubble;
    private LinearLayout panel;

    public WlzOverlayController(Context context) {
        this.context = context;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = context.getSharedPreferences("wlz_settings", Context.MODE_PRIVATE);
    }

    public void show() {
        if (bubble != null) return;
        createBubble();
        createPanel();
    }

    public void close() {
        try { if (bubble != null) wm.removeView(bubble); } catch (Throwable ignored) {}
        try { if (panel != null) wm.removeView(panel); } catch (Throwable ignored) {}
        bubble = null;
        panel = null;
    }

    private void createBubble() {
        bubble = new ImageView(context);
        bubble.setImageResource(R.drawable.wlz_icon);
        bubble.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bubble.setPadding(dp(5), dp(5), dp(5), dp(5));
        bubble.setContentDescription("WLZ floating shortcut");
        bubble.setElevation(dp(12));

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int nx = startX + (int) (e.getRawX() - downX);
                        int ny = startY + (int) (e.getRawY() - downY);
                        if (Math.abs(nx - startX) + Math.abs(ny - startY) > dp(7)) moved = true;
                        bubbleLp.x = Math.max(0, nx);
                        bubbleLp.y = Math.max(dp(4), ny);
                        try { wm.updateViewLayout(bubble, bubbleLp); } catch (Throwable ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) togglePanel();
                        return true;
                    default:
                        return false;
                }
            }
        });

        bubbleLp = overlayLp(dp(58), dp(58), dp(16), dp(240), false);
        add(bubble, bubbleLp);
    }

    private void createPanel() {
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(round(PANEL, ORANGE, 2, 19));

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(context);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("WLZ", 18, ORANGE, true), lp(-1, -2));
        brand.addView(text("RUNTIME // CONTROL", 8, MUTED, true), lp(-1, -2));
        head.addView(brand, lp(0, -2, 1));

        Button close = button("×");
        close.setOnClickListener(v -> hidePanel());
        head.addView(close, lp(dp(42), dp(40)));
        panel.addView(head);

        panel.addView(text("Chạm logo để mở • kéo logo để di chuyển", 8, MUTED, false), top(3));

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < N.length; i++) list.addView(moduleRow(i));
        list.addView(fixLagRow(), top(5));

        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(395)));

        Button launcher = button("MỞ WLZ LAUNCHER");
        launcher.setOnClickListener(v -> {
            Intent i = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(i);
        });
        panel.addView(launcher, top(7));

        panelLp = overlayLp(dp(315), dp(480), dp(12), dp(300), true);
        panel.setVisibility(View.GONE);
        add(panel, panelLp);
    }

    private View moduleRow(int id) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), 0, dp(6), 0);
        row.setBackground(round(ROW, STROKE, 1, 12));

        TextView label = text(N[id], 11, TEXT, true);
        row.addView(label, lp(0, dp(44), 1));

        Switch sw = new Switch(context);
        sw.setChecked(prefs.getBoolean("m_" + id, false));
        sw.setOnCheckedChangeListener((CompoundButton b, boolean enabled) ->
                WlzModuleManager.setModuleEnabled(context, id, enabled));
        row.addView(sw, lp(-2, dp(44)));

        LinearLayout.LayoutParams rp = lp(-1, dp(46));
        rp.bottomMargin = dp(5);
        row.setLayoutParams(rp);
        return row;
    }

    private View fixLagRow() {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(9), dp(7), dp(7), dp(7));
        wrap.setBackground(round(ROW, ORANGE, 1, 13));

        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("FIX LAG", 12, TEXT, true), lp(0, -2, 1));
        head.addView(text("PROFILE", 8, ORANGE, true), lp(-2, -2));
        wrap.addView(head);

        LinearLayout levels = new LinearLayout(context);
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
        prefs.edit()
                .putInt("lag_profile", level)
                .putBoolean("fix_remove_effects", removeEffects)
                .putInt("texture_colors", colors)
                .apply();

        WlzModuleManager.setModuleEnabled(context, 8, true);
        WlzModuleManager.setParam(context, 3, level);
        WlzModuleManager.setParam(context, 4, removeEffects ? 1 : 0);
        WlzModuleManager.setParam(context, 5, colors);
        hidePanel();
    }

    private void togglePanel() {
        if (panel == null) return;
        if (panel.getVisibility() == View.VISIBLE) {
            hidePanel();
            return;
        }
        panelLp.x = Math.max(dp(6), bubbleLp.x - dp(18));
        panelLp.y = Math.max(dp(8), bubbleLp.y + dp(65));
        panel.setVisibility(View.VISIBLE);
        try { wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
    }

    private void hidePanel() {
        if (panel != null) panel.setVisibility(View.GONE);
    }

    private WindowManager.LayoutParams overlayLp(int w, int h, int x, int y, boolean focusable) {
        int flags = focusable
                ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);

        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        return lp;
    }

    private void add(View v, WindowManager.LayoutParams lp) {
        try { wm.addView(v, lp); } catch (Throwable ignored) {}
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(context);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        b.setPadding(dp(7), 0, dp(7), 0);
        b.setBackground(round(Color.rgb(33, 38, 46), STROKE, 1, 10));
        return b;
    }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(width), stroke);
        return d;
    }

    private LinearLayout.LayoutParams lp(int width, int height) {
        return lp(width, height, 0);
    }

    private LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(margin);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
