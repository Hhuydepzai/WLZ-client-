package com.wlz.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String MC_PACKAGE = "com.mojang.minecraftpe";
    private static final String PREFS = "wlz_settings";

    private static final int BG = Color.rgb(10, 11, 14);
    private static final int PANEL = Color.rgb(19, 20, 24);
    private static final int PANEL_2 = Color.rgb(24, 25, 30);
    private static final int STROKE = Color.rgb(52, 54, 61);
    private static final int ORANGE = Color.rgb(246, 115, 20);
    private static final int ORANGE_DARK = Color.rgb(93, 43, 12);
    private static final int ORANGE_LIGHT = Color.rgb(255, 170, 82);
    private static final int TEXT = Color.rgb(245, 245, 247);
    private static final int MUTED = Color.rgb(157, 160, 169);

    private final Map<String, ModuleDef> modules = new LinkedHashMap<>();
    private SharedPreferences prefs;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defineModules();
        try { RuntimeBridge.nativeIsLoaded(); } catch (Throwable ignored) {}

        // Splash is drawn inside the Activity instead of as windowBackground.
        // This avoids startup crashes from resource inflation on some Android builds.
        setContentView(buildSplash());
        handler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) setContentView(buildUi());
        }, 900L);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildSplash() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView icon = new ImageView(this);
        icon.setImageResource(com.wlz.client.R.drawable.wlz_icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(icon, new LinearLayout.LayoutParams(dp(118), dp(118)));

        TextView title = text("WLZ CLIENT", 24, TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, topCentered(14));

        TextView sub = text("Starting client…", 10, ORANGE_LIGHT, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, topCentered(5));
        return root;
    }

    private LinearLayout.LayoutParams topCentered(int margin) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(margin);
        return p;
    }

    private void defineModules() {
        modules.clear();
        add("Zoom", "Phóng camera bằng nút giữ", 0);
        add("FreeLook", "Xoay góc nhìn độc lập", 1);
        add("Ném đồ", "Drop nhanh item đang chọn", 2);
        add("Unlock FPS", "Bỏ giới hạn FPS client", 3);
        add("Fullbright", "Tăng độ sáng thế giới", 4);
        add("Hitbox", "Hiện vùng va chạm entity", 5);
        add("AutoSprint", "Tự chạy khi di chuyển", 6);
        add("Snaplook", "Xoay nhanh theo góc đặt sẵn", 7);
        add("FPS Counter", "Hiện FPS overlay", 8);
    }

    private void add(String name, String desc, int index) {
        modules.put(name, new ModuleDef(name, desc, index));
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(10));
        root.setBackgroundColor(BG);

        root.addView(header(), lp(-1, -2));
        root.addView(gameCard(), top(12));
        root.addView(launchActions(), top(10));
        root.addView(fixLagCard(), top(10));

        TextView heading = text("MODULES", 14, TEXT, true);
        heading.setPadding(dp(2), dp(14), 0, dp(7));
        root.addView(heading, lp(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (ModuleDef module : modules.values()) list.addView(moduleRow(module));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView foot = text("WLZ Client  •  Android 9+  •  ARM64", 10, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(8), 0, 0);
        root.addView(foot, lp(-1, -2));
        return root;
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("WLZ", 30, ORANGE, true);
        logo.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        h.addView(logo, lp(-2, -2));

        TextView title = text("  CLIENT", 19, TEXT, true);
        h.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView chip = text("v0.5", 10, ORANGE_LIGHT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(round(ORANGE_DARK, ORANGE, 1, 18));
        h.addView(chip, lp(-2, -2));
        return h;
    }

    private View gameCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(round(PANEL, STROKE, 1, 17));

        card.addView(text("MINECRAFT BEDROCK", 11, ORANGE_LIGHT, true), lp(-1, -2));

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text("Launcher", 25, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView mc = text("com.mojang.minecraftpe", 9, MUTED, false);
        mc.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(mc, lp(-2, -2));
        card.addView(line, top(2));

        status = text("Đang kiểm tra Minecraft…", 10, MUTED, false);
        status.setPadding(0, dp(5), 0, 0);
        card.addView(status, lp(-1, -2));
        return card;
    }

    private View launchActions() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button launch = actionButton("▶  CHẠY MINECRAFT", ORANGE, TEXT);
        launch.setOnClickListener(v -> launchMinecraft());
        row.addView(launch, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button overlay = actionButton("◉  OVERLAY", PANEL_2, TEXT);
        overlay.setOnClickListener(v -> openOverlaySettings());
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(0, dp(52), 0.62f);
        olp.leftMargin = dp(8);
        row.addView(overlay, olp);
        return row;
    }

    private View fixLagCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(11));
        card.setBackground(round(PANEL, STROKE, 1, 15));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("FIX LAG", 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(profileName(prefs.getInt("lag_profile", 1)), 9, ORANGE_LIGHT, true);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badge.setBackground(round(ORANGE_DARK, ORANGE, 1, 13));
        head.addView(badge, lp(-2, -2));
        card.addView(head);

        card.addView(text("Một bảng duy nhất, chỉ dùng tông cam. Chọn mức giảm render:", 10, MUTED, false), top(4));

        LinearLayout levels = new LinearLayout(this);
        String[] labels = {"NHẸ", "MẠNH", "SIÊU"};
        int current = prefs.getInt("lag_profile", 1);
        for (int i = 1; i <= 3; i++) {
            final int level = i;
            Button b = actionButton(labels[i - 1], level == current ? ORANGE : PANEL_2, TEXT);
            b.setOnClickListener(v -> { applyFixProfile(level); recreate(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
            if (i > 1) p.leftMargin = dp(6);
            levels.addView(b, p);
        }
        card.addView(levels, top(8));

        Switch effects = new Switch(this);
        effects.setText("  Xóa hiệu ứng nặng");
        effects.setTextColor(TEXT);
        effects.setTextSize(11);
        effects.setChecked(prefs.getBoolean("fix_remove_effects", current >= 2));
        effects.setOnCheckedChangeListener((CompoundButton b, boolean c) -> {
            prefs.edit().putBoolean("fix_remove_effects", c).apply();
            applyFixProfile(prefs.getInt("lag_profile", 1));
        });
        card.addView(effects, top(2));

        card.addView(text("Cấp 1: 4 màu  •  Cấp 2: 4 màu  •  Cấp 3: 3 màu", 9, MUTED, false), top(1));
        return card;
    }

    private View moduleRow(ModuleDef module) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(round(PANEL_2, STROKE, 1, 14));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(module.name, 13, TEXT, true), lp(-1, -2));
        copy.addView(text(module.desc, 9, MUTED, false), top(2));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        Switch sw = new Switch(this);
        boolean checked = prefs.getBoolean(module.key(), false);
        sw.setChecked(checked);
        syncNative(module.index, checked);
        sw.setOnCheckedChangeListener((CompoundButton b, boolean enabled) -> {
            prefs.edit().putBoolean(module.key(), enabled).apply();
            syncNative(module.index, enabled);
        });
        row.addView(sw, lp(-2, -2));

        LinearLayout.LayoutParams wrap = lp(-1, -2);
        wrap.bottomMargin = dp(7);
        row.setLayoutParams(wrap);
        return row;
    }

    private void applyFixProfile(int level) {
        level = Math.max(1, Math.min(3, level));
        boolean removeEffects = prefs.getBoolean("fix_remove_effects", level >= 2);
        int colors = level >= 3 ? 3 : 4;
        prefs.edit().putInt("lag_profile", level)
                .putBoolean("fix_remove_effects", removeEffects)
                .putInt("texture_colors", colors)
                .apply();
        syncNative(8, true);
        setNativeParam(3, level);
        setNativeParam(4, removeEffects ? 1 : 0);
        setNativeParam(5, colors);
    }

    private String profileName(int level) {
        if (level == 1) return "NHẸ";
        if (level == 2) return "MẠNH";
        return "SIÊU";
    }

    private void syncNative(int index, boolean enabled) {
        try { RuntimeBridge.nativeSetModule(index, enabled); } catch (Throwable ignored) {}
    }

    private void setNativeParam(int key, int value) {
        try { RuntimeBridge.nativeSetParam(key, value); } catch (Throwable ignored) {}
    }

    private void maybeStartOverlay() {
        if (!Settings.canDrawOverlays(this)) return;
        try {
            Intent i = new Intent(this, OverlayService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Throwable ignored) {}
    }

    private void launchMinecraft() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(MC_PACKAGE);
        if (intent == null) {
            Toast.makeText(this, "Chưa cài Minecraft Bedrock.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            // Let Minecraft finish its process/activity startup before showing the system overlay.
            handler.postDelayed(this::maybeStartOverlay, 1800L);
        } catch (Throwable e) {
            Toast.makeText(this, "Không thể mở Minecraft.", Toast.LENGTH_LONG).show();
        }
    }

    private void openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            maybeStartOverlay();
            Toast.makeText(this, "WLZ Overlay đã bật.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean installed = getPackageManager().getLaunchIntentForPackage(MC_PACKAGE) != null;
        status.setText(installed ? "● Minecraft đã sẵn sàng" : "● Chưa tìm thấy Minecraft trên máy");
        status.setTextColor(installed ? ORANGE_LIGHT : MUTED);
    }

    private Button actionButton(String title, int fill, int color) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(color);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(round(fill, fill == ORANGE ? ORANGE_LIGHT : STROKE, 1, 13));
        return b;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int width, int height) { return lp(width, height, 0); }
    private LinearLayout.LayoutParams lp(int width, int height, float weight) {
        return new LinearLayout.LayoutParams(width, height, weight);
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(margin);
        return p;
    }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(width), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ModuleDef {
        final String name;
        final String desc;
        final int index;
        ModuleDef(String name, String desc, int index) { this.name = name; this.desc = desc; this.index = index; }
        String key() { return "m_" + index; }
    }
}
