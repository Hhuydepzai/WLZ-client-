package com.wlz.client;

import android.app.Activity;
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
    private static final int BG = Color.rgb(7, 9, 12);
    private static final int PANEL = Color.rgb(15, 18, 23);
    private static final int STROKE = Color.rgb(47, 54, 64);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int TEXT = Color.rgb(242, 245, 249);
    private static final int MUTED = Color.rgb(145, 154, 166);

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
        root.addView(buildLauncher(), new FrameLayout.LayoutParams(-1, -1));

        updateRuntimeStatus();
    }

    private View buildLauncher() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(24));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(18));
        card.setBackground(round(PANEL, STROKE, 1, 20));

        WlzLogoView logo = new WlzLogoView(this);
        card.addView(logo, centered(dp(116), dp(116)));

        TextView title = text("WLZ LAUNCHER", 23, TEXT, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, topCentered(12));

        TextView subtitle = text("BEDROCK CLIENT", 10, ORANGE, true);
        subtitle.setGravity(Gravity.CENTER);
        card.addView(subtitle, topCentered(4));

        status = text("ĐANG KIỂM TRA RUNTIME...", 9, MUTED, true);
        status.setGravity(Gravity.CENTER);
        card.addView(status, topCentered(12));

        Button play = button("CHƠI MINECRAFT");
        play.setTextSize(12);
        play.setBackground(round(ORANGE, ORANGE, 1, 14));
        play.setOnClickListener(v -> launchMinecraft());
        card.addView(play, topCenteredButton(dp(52), 16));

        Button map = button("MAP PHÍM + CHỈNH HUD");
        map.setOnClickListener(v ->
                startActivity(new Intent(this, WlzControlEditorActivity.class)));
        card.addView(map, topCenteredButton(dp(48), 8));

        TextView info = text(
                "Zoom  •  FreeLook  •  Unlock FPS  •  Fullbright\n"
                        + "OTG keymap  •  touch controls  •  ClickGUI",
                9, MUTED, false);
        info.setGravity(Gravity.CENTER);
        card.addView(info, topCentered(12));

        page.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView footer = text("WLZ CLIENT 1.0.0  •  arm64-v8a", 8, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer, topCentered(14));

        return page;
    }

    private void updateRuntimeStatus() {
        if (hasMinecraftRuntime()) {
            status.setText("RUNTIME SẴN  •  ARM64");
        } else {
            status.setText("THIẾU MINECRAFT RUNTIME");
        }
    }

    private void launchMinecraft() {
        if (!hasMinecraftRuntime()) {
            status.setText("MINECRAFT RUNTIME KHÔNG SẴN SÀNG");
            Toast.makeText(this,
                    "WLZ chưa có Minecraft runtime tích hợp.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Intent intent = new Intent();
            intent.setClassName(this, MC_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Throwable e) {
            status.setText("KHỞI ĐỘNG MINECRAFT THẤT BẠI");
            Toast.makeText(this,
                    "Không thể mở Minecraft runtime.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) updateRuntimeStatus();
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
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
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
