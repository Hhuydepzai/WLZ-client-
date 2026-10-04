package com.wlz.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
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

    private final Map<String, ModuleDef> modules = new LinkedHashMap<String, ModuleDef>();
    private SharedPreferences prefs;
    private FrameLayout root;
    private View clickGui;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        defineModules();

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        root.addView(buildSplash(), new FrameLayout.LayoutParams(-1, -1));
        root.postDelayed(new Runnable() {
            @Override public void run() {
                if (!isFinishing() && !isDestroyed()) {
                    buildHome();
                }
            }
        }, 520L);
    }

    private View buildSplash() {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setPadding(dp(28), dp(28), dp(28), dp(28));
        splash.setBackgroundColor(BG);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.wlz_icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        splash.addView(icon, centered(dp(112), dp(112)));

        TextView title = text("WLZ Client", 27, TEXT, true);
        title.setGravity(Gravity.CENTER);
        splash.addView(title, topCentered(13));

        TextView sub = text("Play Smarter - Not Harder", 11, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        splash.addView(sub, topCentered(4));

        View line = new View(this);
        line.setBackgroundColor(ORANGE);
        splash.addView(line, centered(dp(120), dp(2), 17));

        TextView loading = text("Đang khởi động…", 10, ORANGE_LIGHT, true);
        loading.setGravity(Gravity.CENTER);
        splash.addView(loading, topCentered(12));

        return splash;
    }

    private void buildHome() {
        root.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(15), dp(13), dp(15), dp(11));
        page.setBackgroundColor(BG);

        page.addView(header(), lp(-1, -2));
        page.addView(hero(), top(11));

        Button start = primaryButton("▶  VÀO MINECRAFT", ORANGE);
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                launchMinecraft();
            }
        });
        page.addView(start, top(10, dp(54)));

        LinearLayout mini = new LinearLayout(this);
        mini.addView(infoButton("●  CLIENT SẴN SÀNG"), new LinearLayout.LayoutParams(0, dp(42), 1));
        Button controls = infoButton("⌘  MAP PHÍM");
        controls.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WlzControlEditorActivity.class));
            }
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(42), 0.92f);
        cp.leftMargin = dp(7);
        mini.addView(controls, cp);
        page.addView(mini, top(8));

        TextView hint = text("Các chức năng nằm trong nút WLZ tròn. Không còn hàng switch nằm ngoài màn hình.", 9, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint, top(10));

        ScrollView scroll = new ScrollView(this);
        LinearLayout spacer = new LinearLayout(this);
        spacer.setOrientation(LinearLayout.VERTICAL);
        spacer.addView(runtimeInfo(), lp(-1, -2));
        spacer.addView(fixLagCard(), top(8));
        spacer.addView(footer(), top(10));
        scroll.addView(spacer);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        createCircle();
        createClickGui();
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = text("WLZ", 27, ORANGE, true);
        logo.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        h.addView(logo, lp(-2, -2));

        TextView title = text("  CLIENT", 15, TEXT, true);
        h.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView badge = text("0.6.4", 10, ORANGE_LIGHT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(round(ORANGE_DARK, ORANGE, 1, 16));
        h.addView(badge, lp(-2, -2));
        return h;
    }

    private View hero() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(14));
        card.setBackground(round(PANEL, ORANGE, 1, 17));

        TextView label = text("WLZ CLIENT", 11, ORANGE_LIGHT, true);
        card.addView(label, lp(-1, -2));

        TextView title = text("Play Smarter - Not Harder", 22, TEXT, true);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, top(5));

        TextView sub = text("Minecraft Bedrock • ARM64 • Android 9+", 10, MUTED, false);
        card.addView(sub, top(4));

        TextView tiny = text("Launch → client runtime → in-game controls", 9, MUTED, false);
        card.addView(tiny, top(8));
        return card;
    }

    private View runtimeInfo() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        card.setBackground(round(PANEL_2, STROKE, 1, 14));

        card.addView(text("CLIENT CONTROL", 11, ORANGE_LIGHT, true), lp(-1, -2));
        card.addView(text("Bật/tắt module từ ClickGUI WLZ.", 10, TEXT, true), top(4));
        card.addView(text("Map phím OTG + nút cảm ứng được lưu riêng theo máy.", 9, MUTED, false), top(3));
        return card;
    }

    private View fixLagCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(PANEL, STROKE, 1, 14));

        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(text("FIX LAG", 12, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        h.addView(text(profileName(prefs.getInt("lag_profile", 1)), 9, ORANGE_LIGHT, true), lp(-2, -2));
        card.addView(h);

        LinearLayout levels = new LinearLayout(this);
        final String[] labels = {"NHẸ", "MẠNH", "SIÊU"};
        int current = prefs.getInt("lag_profile", 1);
        for (int i = 1; i <= 3; i++) {
            final int level = i;
            Button b = infoButton(labels[i - 1]);
            if (current == i) b.setBackground(round(ORANGE, ORANGE_LIGHT, 1, 10));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    applyFix(level);
                    buildHome();
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(37), 1);
            if (i > 1) p.leftMargin = dp(5);
            levels.addView(b, p);
        }
        card.addView(levels, top(7));

        Switch effects = new Switch(this);
        effects.setText("  Xóa hiệu ứng nặng");
        effects.setTextColor(TEXT);
        effects.setTextSize(10);
        effects.setChecked(prefs.getBoolean("fix_remove_effects", current >= 2));
        effects.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("fix_remove_effects", isChecked).apply();
            applyFix(prefs.getInt("lag_profile", 1));
        });
        card.addView(effects, top(2));
        return card;
    }

    private void applyFix(int level) {
        boolean remove = prefs.getBoolean("fix_remove_effects", level >= 2);
        int colors = level >= 3 ? 3 : 4;
        prefs.edit().putInt("lag_profile", level)
                .putBoolean("fix_remove_effects", remove)
                .putInt("texture_colors", colors)
                .apply();
        WlzModuleManager.setModuleEnabled(this, 8, true);
        WlzModuleManager.setParam(this, 3, level);
        WlzModuleManager.setParam(this, 4, remove ? 1 : 0);
        WlzModuleManager.setParam(this, 5, colors);
    }

    private void defineModules() {
        modules.clear();
        add("Zoom", "Phóng camera", 0);
        add("FreeLook", "Xoay góc nhìn độc lập", 1);
        add("Ném đồ", "Drop nhanh item", 2);
        add("Unlock FPS", "Mục tiêu FPS", 3);
        add("Fullbright", "Tăng độ sáng", 4);
        add("Hitbox", "Vùng va chạm", 5);
        add("AutoSprint", "Tự chạy", 6);
        add("Snaplook", "Xoay theo góc đặt", 7);
        add("FPS Counter", "Bộ đếm FPS", 8);
    }

    private void add(String name, String desc, int index) {
        modules.put(name, new ModuleDef(name, desc, index));
    }

    private void createCircle() {
        Button circle = new Button(this);
        circle.setText("WLZ");
        circle.setTextColor(TEXT);
        circle.setTextSize(12);
        circle.setAllCaps(false);
        circle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        circle.setBackground(circleBackground());
        circle.setContentDescription("WLZ ClickGUI");

        circle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                clickGui.setVisibility(clickGui.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.RIGHT | Gravity.BOTTOM);
        p.rightMargin = dp(18);
        p.bottomMargin = dp(26);
        root.addView(circle, p);
    }

    private void createClickGui() {
        clickGui = buildClickGui();
        clickGui.setVisibility(View.GONE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(320), dp(520), Gravity.RIGHT | Gravity.BOTTOM);
        p.rightMargin = dp(10);
        p.bottomMargin = dp(92);
        root.addView(clickGui, p);
    }

    private View buildClickGui() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(11), dp(11), dp(11), dp(11));
        panel.setBackground(round(PANEL, ORANGE, 2, 18));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("WLZ", 18, ORANGE, true);
        head.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button keys = infoButton("MAP PHÍM");
        keys.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WlzControlEditorActivity.class));
            }
        });
        head.addView(keys, lp(dp(105), dp(38)));
        panel.addView(head);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (ModuleDef m : modules.values()) {
            list.addView(moduleRow(m));
        }
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button close = infoButton("ĐÓNG");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                clickGui.setVisibility(View.GONE);
            }
        });
        panel.addView(close, top(7));
        return panel;
    }

    private View moduleRow(final ModuleDef module) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(3), dp(5), dp(3));
        row.setBackground(round(PANEL_2, STROKE, 1, 11));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(module.name, 11, TEXT, true), lp(-1, -2));
        copy.addView(text(module.desc, 8, MUTED, false), top(1));
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));

        Switch sw = new Switch(this);
        sw.setChecked(WlzModuleManager.isModuleEnabled(this, module.index));
        sw.setOnCheckedChangeListener((buttonView, isChecked) ->
                WlzModuleManager.setModuleEnabled(MainActivity.this, module.index, isChecked));
        row.addView(sw, lp(-2, dp(48)));

        LinearLayout.LayoutParams rp = lp(-1, dp(52));
        rp.bottomMargin = dp(5);
        row.setLayoutParams(rp);
        return row;
    }

    private void launchMinecraft() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(MC_PACKAGE);
        if (intent == null) {
            Toast.makeText(this, "Chưa có Minecraft Bedrock.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Throwable e) {
            Toast.makeText(this, "Không mở được Minecraft.", Toast.LENGTH_LONG).show();
        }
    }

    private Button primaryButton(String title, int fill) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(TEXT);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setBackground(round(fill, ORANGE_LIGHT, 1, 14));
        return b;
    }

    private Button infoButton(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(round(PANEL_2, STROKE, 1, 11));
        return b;
    }

    private TextView footer() {
        TextView t = text("WLZ Client 0.6.4 • ARM64 • no overlay permission", 8, MUTED, false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private String profileName(int level) {
        if (level == 1) return "NHẸ";
        if (level == 2) return "MẠNH";
        return "SIÊU";
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        return t;
    }

    private FrameLayout.LayoutParams centered(int w, int h) {
        return centered(w, h, 0);
    }

    private FrameLayout.LayoutParams centered(int w, int h, int top) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, Gravity.CENTER_HORIZONTAL);
        p.topMargin = dp(top);
        return p;
    }

    private FrameLayout.LayoutParams lp(int w, int h) {
        return new FrameLayout.LayoutParams(w, h);
    }

    private FrameLayout.LayoutParams top(int margin) {
        return top(margin, -2);
    }

    private FrameLayout.LayoutParams top(int margin, int height) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, height);
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

    private GradientDrawable circleBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ORANGE);
        d.setStroke(dp(2), TEXT);
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
