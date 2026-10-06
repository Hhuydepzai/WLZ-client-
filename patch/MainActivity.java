package com.wlz.client;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String MC_ACTIVITY = "com.mojang.minecraftpe.MainActivity";
    private static final int BG = Color.rgb(7, 9, 12);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int TEXT = Color.rgb(242, 245, 249);
    private static final int MUTED = Color.rgb(145, 154, 166);

    private FrameLayout root;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        root.addView(buildSplash(), new FrameLayout.LayoutParams(-1, -1));

        root.postDelayed(new Runnable() {
            @Override public void run() {
                launchMinecraft();
            }
        }, 750L);
    }

    private View buildSplash() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        WlzLogoView logo = new WlzLogoView(this);
        box.addView(logo, centered(dp(128), dp(128)));

        TextView title = text("WLZ CLIENT", 22, TEXT, true);
        title.setGravity(Gravity.CENTER);
        box.addView(title, topCentered(14));

        View line = new View(this);
        line.setBackgroundColor(ORANGE);
        box.addView(line, centered(dp(92), dp(2), 10));

        status = text("ĐANG KHỞI ĐỘNG", 9, MUTED, true);
        status.setGravity(Gravity.CENTER);
        box.addView(status, topCentered(10));
        return box;
    }

    private void launchMinecraft() {
        if (!hasMinecraftRuntime()) {
            status.setText("MISSING MINECRAFT RUNTIME");
            Toast.makeText(this,
                    "WLZ chưa được gắn Minecraft runtime.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            android.content.Intent intent = new android.content.Intent();
            intent.setClassName(this, MC_ACTIVITY);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        } catch (Throwable e) {
            status.setText("MINECRAFT START FAILED");
            Toast.makeText(this, "Minecraft runtime khởi động lỗi.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasMinecraftRuntime() {
        try {
            Class.forName(MC_ACTIVITY, false, getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams centered(int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private LinearLayout.LayoutParams centered(int w, int h, int top) {
        LinearLayout.LayoutParams p = centered(w, h);
        p.topMargin = dp(top);
        return p;
    }

    private LinearLayout.LayoutParams topCentered(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(top);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
