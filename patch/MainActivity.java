package com.wlz.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String MC_ACTIVITY = "com.mojang.minecraftpe.MainActivity";

    private static final int BG = Color.WHITE;
    private static final int PANEL = Color.rgb(248, 248, 248);
    private static final int STROKE = Color.rgb(230, 230, 230);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int TEXT = Color.rgb(28, 28, 30);
    private static final int MUTED = Color.rgb(115, 118, 124);

    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(26), dp(24), dp(26), dp(24));

        TextView brand = text("WLZ CLIENT", 26, ORANGE, true);
        brand.setGravity(Gravity.CENTER);
        page.addView(brand, topCentered(4));

        TextView subtitle = text("BEDROCK • ARM64", 10, MUTED, true);
        subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle, topCentered(3));

        WlzLogoView logo = new WlzLogoView(this);
        page.addView(logo, centered(dp(178), dp(132)));

        status = text("ĐANG KIỂM TRA GAME RUNTIME...", 9, MUTED, true);
        status.setGravity(Gravity.CENTER);
        page.addView(status, topCentered(8));

        Button play = button("▶  CHƠI MINECRAFT");
        play.setTextSize(13);
        play.setTextColor(Color.WHITE);
        play.setBackground(round(ORANGE, ORANGE, 1, 16));
        play.setOnClickListener(v -> launchMinecraft());
        page.addView(play, topCenteredButton(dp(54), 14));

        TextView hint = text(
                "Trong game: chạm nút tròn WLZ để mở ClickGUI.\n"
                        + "MAP PHÍM + HOTKEY nằm bên trong nút tròn.",
                9, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint, topCentered(10));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, -1);
        pp.gravity = Gravity.CENTER;
        root.addView(page, pp);

        updateRuntimeStatus();
    }

    private void updateRuntimeStatus() {
        status.setText(hasMinecraftRuntime()
                ? "RUNTIME SẴN • SẴN SÀNG"
                : "THIẾU MINECRAFT RUNTIME");
    }

    private void launchMinecraft() {
        try {
            Class<?> activityClass = Class.forName(MC_ACTIVITY, false, getClassLoader());
            Intent intent = new Intent(this, activityClass);
            intent.setComponent(new ComponentName(this, activityClass));
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            finish();
        } catch (Throwable e) {
            status.setText("KHÔNG MỞ ĐƯỢC MINECRAFT");
            Toast.makeText(this, "WLZ không khởi động được Minecraft runtime.", Toast.LENGTH_LONG).show();
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

    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams centered(int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private LinearLayout.LayoutParams topCentered(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(top);
        return p;
    }

    private LinearLayout.LayoutParams topCenteredButton(int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, h);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(top);
        return p;
    }

    private android.graphics.drawable.GradientDrawable round(int fill, int stroke, int width, int radius) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(width), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
