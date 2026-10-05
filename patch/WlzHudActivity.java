package com.wlz.client;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class WlzHudActivity extends Activity {
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int ORANGE_LIGHT = Color.rgb(255, 171, 76);
    private static final int PANEL = Color.rgb(10, 12, 16);
    private static final int PANEL_2 = Color.rgb(18, 21, 27);
    private static final int STROKE = Color.rgb(52, 59, 70);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(150, 158, 170);

    private final String[] names = {
            "Zoom", "FreeLook", "Ném đồ", "Unlock FPS", "Fullbright",
            "Hitbox", "AutoSprint", "Snaplook", "FPS Counter"
    };
    private final String[] desc = {
            "Phóng camera", "Xoay góc nhìn", "Drop nhanh item", "Bỏ giới hạn FPS",
            "Tăng độ sáng", "Vùng va chạm", "Tự chạy", "Xoay nhanh", "Đếm FPS"
    };

    private WindowManager.LayoutParams windowLp;
    private Button circle;
    private LinearLayout panel;
    private boolean expanded;
    private float downX, downY;
    private int startX, startY;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        w.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

        windowLp = new WindowManager.LayoutParams(
                dp(66), dp(66),
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                -3);
        windowLp.gravity = Gravity.TOP | Gravity.LEFT;
        SharedPreferences p = prefs();

        windowLp.x = p.getInt("hud_x", dp(14));
        windowLp.y = p.getInt("hud_y", dp(140));

        circle = makeCircle();
        setContentView(circle);
        getWindow().setAttributes(windowLp);
    }

    private Button makeCircle() {
        Button b = new Button(this);
        b.setText("WLZ");
        b.setTextColor(TEXT);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setBackground(circleBg());
        b.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getRawX();
                downY = e.getRawY();
                startX = windowLp.x;
                startY = windowLp.y;
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int nx = startX + (int)(e.getRawX() - downX);
                int ny = startY + (int)(e.getRawY() - downY);
                windowLp.x = Math.max(0, nx);
                windowLp.y = Math.max(0, ny);
                getWindow().setAttributes(windowLp);
                prefs().edit().putInt("hud_x", windowLp.x).putInt("hud_y", windowLp.y).apply();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = Math.abs(e.getRawX() - downX);
                float dy = Math.abs(e.getRawY() - downY);
                if (dx < dp(8) && dy < dp(8)) togglePanel();
                return true;
            }
            return true;
        });
        return b;
    }

    private void togglePanel() {
        expanded = !expanded;
        if (expanded) {
            panel = buildPanel();
            setContentView(panel);
            windowLp.width = dp(320);
            windowLp.height = dp(520);
            windowLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowLp.x = Math.max(0, windowLp.x - dp(12));
            windowLp.y = Math.max(0, windowLp.y - dp(12));
            getWindow().setAttributes(windowLp);
        } else {
            circle = makeCircle();
            setContentView(circle);
            windowLp.width = dp(66);
            windowLp.height = dp(66);
            windowLp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            getWindow().setAttributes(windowLp);
        }
    }

    private LinearLayout buildPanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackground(round(PANEL, ORANGE, 2, 18));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text("WLZ  CLICKGUI", 16, ORANGE, true);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));

        Button close = button("ĐÓNG");
        close.setOnClickListener(v -> togglePanel());
        head.addView(close, lp(dp(86), dp(38)));
        root.addView(head);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(4), dp(5), dp(4));
            row.setBackground(round(PANEL_2, STROKE, 1, 12));

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(text(names[i], 11, TEXT, true));
            copy.addView(text(desc[i], 8, MUTED, false), top(1));
            row.addView(copy, new LinearLayout.LayoutParams(0, dp(50), 1));

            Switch sw = new Switch(this);
            sw.setChecked(WlzModuleManager.isModuleEnabled(this, index));
            sw.setOnCheckedChangeListener((buttonView, enabled) ->
                    WlzModuleManager.setModuleEnabled(WlzHudActivity.this, index, enabled));
            row.addView(sw, lp(-2, dp(50)));

            LinearLayout.LayoutParams rp = lp(-1, dp(55));
            rp.bottomMargin = dp(5);
            list.addView(row, rp);
        }

        LinearLayout fix = new LinearLayout(this);
        fix.setOrientation(LinearLayout.VERTICAL);
        fix.setPadding(dp(8), dp(8), dp(8), dp(8));
        fix.setBackground(round(PANEL_2, STROKE, 1, 12));
        fix.addView(text("FIX LAG", 11, ORANGE_LIGHT, true));
        fix.addView(text("Hồ sơ hiện tại: " + profile(), 9, MUTED, false), top(2));

        LinearLayout levels = new LinearLayout(this);
        String[] labels = {"NHẸ", "MẠNH", "SIÊU"};
        for (int i = 1; i <= 3; i++) {
            final int level = i;
            Button b = button(labels[i - 1]);
            b.setOnClickListener(v -> {
                SharedPreferences sp = prefs();
                sp.edit().putInt("lag_profile", level).apply();
                WlzModuleManager.setParam(this, 3, level);
                Toast.makeText(this, "Fix Lag " + labels[level - 1], Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 1) bp.leftMargin = dp(5);
            levels.addView(b, bp);
        }
        fix.addView(levels, top(6));
        list.addView(fix, top(4));

        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button back = button("ẨN WLZ");
        back.setOnClickListener(v -> togglePanel());
        root.addView(back, top(7));
        return root;
    }

    private String profile() {
        int p = prefs().getInt("lag_profile", 1);
        return p == 3 ? "SIÊU" : (p == 2 ? "MẠNH" : "NHẸ");
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("wlz_settings", Context.MODE_PRIVATE);
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(round(PANEL_2, STROKE, 1, 10));
        return b;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        return t;
    }

    private GradientDrawable circleBg() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ORANGE);
        d.setStroke(dp(2), TEXT);
        return d;
    }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(width), stroke);
        return d;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams top(int m) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(m);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
