package com.wlz.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

    private static final int BG = Color.rgb(7, 9, 12);
    private static final int PANEL = Color.rgb(15, 18, 23);
    private static final int PANEL_2 = Color.rgb(20, 24, 30);
    private static final int STROKE = Color.rgb(47, 54, 64);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int ORANGE_LIGHT = Color.rgb(255, 171, 76);
    private static final int ORANGE_DARK = Color.rgb(77, 34, 5);
    private static final int TEXT = Color.rgb(242, 245, 249);
    private static final int MUTED = Color.rgb(145, 154, 166);
    private static final int GREEN = Color.rgb(65, 210, 136);

    private final Map<String, ModuleDef> modules = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defineModules();

        setContentView(buildSplash());
        handler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                setContentView(buildUi());
            }
        }, 700L);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private View buildSplash() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(BG);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.wlz_icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(118), dp(118));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, ilp);

        TextView title = text("WLZ Client", 27, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, topCentered(14));

        TextView subtitle = text("Play Smarter - Not Harder", 11, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, topCentered(4));

        View line = new View(this);
        line.setBackgroundColor(ORANGE);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(dp(150), dp(2));
        lineLp.gravity = Gravity.CENTER_HORIZONTAL;
        lineLp.topMargin = dp(18);
        root.addView(line, lineLp);

        TextView loading = text("Đang khởi động…", 10, ORANGE_LIGHT, true);
        loading.setGravity(Gravity.CENTER);
        root.addView(loading, topCentered(14));

        TextView footer = text("ANDROID 9+  •  ARM64-V8A", 8, MUTED, true);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, topCentered(22));

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
        add("Unlock FPS", "Thiết lập mục tiêu FPS", 3);
        add("Fullbright", "Thiết lập độ sáng", 4);
        add("Hitbox", "Hiện vùng va chạm entity", 5);
        add("AutoSprint", "Tự chạy khi di chuyển", 6);
        add("Snaplook", "Xoay nhanh theo góc đặt sẵn", 7);
        add("FPS Counter", "Hiện bộ đếm FPS", 8);
    }

    private void add(String name, String desc, int index) {
        modules.put(name, new ModuleDef(name, desc, index));
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(13), dp(16), dp(10));
        root.setBackgroundColor(BG);

        root.addView(header(), lp(-1, -2));
        root.addView(runtimeCard(), top(12));
        root.addView(launchActions(), top(10));
        root.addView(fixLagCard(), top(10));

        TextView heading = text("MODULES  //  " + String.format("%02d", modules.size()), 13, TEXT, true);
        heading.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        heading.setPadding(dp(2), dp(15), 0, dp(8));
        root.addView(heading, lp(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (ModuleDef module : modules.values()) list.addView(moduleRow(module));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView foot = text("WLZ CLIENT 0.6.2  •  ARM64  •  ORANGE / GRAPHITE", 9, MUTED, false);
        foot.setTypeface(Typeface.MONOSPACE);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(8), 0, 0);
        root.addView(foot, lp(-1, -2));
        return root;
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("WLZ", 27, ORANGE, true);
        logo.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        h.addView(logo, lp(-2, -2));

        TextView title = text("  CLIENT / CONTROL", 15, TEXT, true);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        h.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView chip = text("0.6.2", 10, ORANGE_LIGHT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(round(ORANGE_DARK, ORANGE, 1, 18));
        h.addView(chip, lp(-2, -2));
        return h;
    }

    private View runtimeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(round(PANEL, STROKE, 1, 16));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("WLZ RUNTIME", 11, ORANGE_LIGHT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text("READY", 9, ORANGE_LIGHT, true);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badge.setBackground(round(ORANGE_DARK, ORANGE, 1, 12));
        top.addView(badge, lp(-2, -2));
        card.addView(top);

        TextView title = text("Launch → Preload → Overlay", 21, TEXT, true);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        card.addView(title, top(5));

        status = text("Đang kiểm tra Minecraft…", 10, MUTED, false);
        status.setTypeface(Typeface.MONOSPACE);
        card.addView(status, top(5));

        TextView hint = text(
                "Launcher WLZ độc lập với preload service, module manager và floating shortcut.",
                9, MUTED, false);
        card.addView(hint, top(7));
        return card;
    }

    private View launchActions() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        Button launch = actionButton("▶  CHẠY MINECRAFT", ORANGE, TEXT);
        launch.setOnClickListener(v -> launchMinecraft());
        row.addView(launch, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button runtime = actionButton("◉  BẬT CLIENT", PANEL_2, TEXT);
        runtime.setOnClickListener(v -> activateClient());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(52), 0.68f);
        rlp.leftMargin = dp(8);
        row.addView(runtime, rlp);
        return row;
    }

    private View fixLagCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(11));
        card.setBackground(round(PANEL, STROKE, 1, 15));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("FIX LAG", 13, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(profileName(prefs.getInt("lag_profile", 1)), 9, ORANGE_LIGHT, true);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badge.setBackground(round(ORANGE_DARK, ORANGE, 1, 13));
        head.addView(badge, lp(-2, -2));
        card.addView(head);

        card.addView(text("Chọn profile giảm tải render của WLZ.", 9, MUTED, false), top(4));

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
        boolean checked = WlzModuleManager.isModuleEnabled(this, module.index);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((CompoundButton b, boolean enabled) -> {
            WlzModuleManager.setModuleEnabled(this, module.index, enabled);
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
        WlzModuleManager.setModuleEnabled(this, 8, true);
        WlzModuleManager.setParam(this, 3, level);
        WlzModuleManager.setParam(this, 4, removeEffects ? 1 : 0);
        WlzModuleManager.setParam(this, 5, colors);
    }

    private String profileName(int level) {
        if (level == 1) return "NHẸ";
        if (level == 2) return "MẠNH";
        return "SIÊU";
    }

    private void launchMinecraft() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(MC_PACKAGE);
        if (intent == null) {
            Toast.makeText(this, "Chưa cài Minecraft Bedrock.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));

            if (Settings.canDrawOverlays(this)) {
                showOverlayService();
            }
        } catch (Throwable e) {
            Toast.makeText(this, "Không thể mở Minecraft.", Toast.LENGTH_LONG).show();
        }
    }

    private void activateClient() {
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
            return;
        }

        showOverlayService();
        Toast.makeText(this, "WLZ floating shortcut đã khởi động.", Toast.LENGTH_SHORT).show();
    }

    private void showOverlayService() {
        Intent intent = new Intent(this, WlzPreloadService.class)
                .setAction(WlzPreloadService.ACTION_SHOW_OVERLAY);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        } catch (Throwable e) {
            Toast.makeText(this, "Không thể khởi động WLZ overlay.", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean installed = getPackageManager().getLaunchIntentForPackage(MC_PACKAGE) != null;
        status.setText(installed ? "● Minecraft đã sẵn sàng" : "● Chưa tìm thấy Minecraft trên máy");
        status.setTextColor(installed ? GREEN : MUTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private Button actionButton(String title, int fill, int color) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(color);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(round(fill, fill == ORANGE ? ORANGE_LIGHT : STROKE, 1, 13));
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

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
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
        ModuleDef(String name, String desc, int index) {
            this.name = name;
            this.desc = desc;
            this.index = index;
        }
    }
}
