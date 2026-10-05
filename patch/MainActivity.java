package com.wlz.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
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
    private static final String MC_ACTIVITY = "com.mojang.minecraftpe.MainActivity";
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
        icon.setImageDrawable(makeLogo());
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

    private LinearLayout.LayoutParams topCentered(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(margin);
        return p;
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

        TextView hint = text("WLZ • module control + Fix Lag nằm trong nút tròn.", 9, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint, top(8));

        ScrollView scroll = new ScrollView(this);
        LinearLayout spacer = new LinearLayout(this);
        spacer.setOrientation(LinearLayout.VERTICAL);
        spacer.addView(runtimeInfo(), lp(-1, -2));
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

        TextView badge = text("0.7.1", 10, ORANGE_LIGHT, true);
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

        TextView sub = text("Minecraft Bedrock runtime tích hợp • ARM64 • Android 9+", 10, MUTED, false);
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
        final Button circle = new Button(this);
        circle.setText("WLZ");
        circle.setTextColor(TEXT);
        circle.setTextSize(12);
        circle.setAllCaps(false);
        circle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        circle.setBackground(circleBackground());
        circle.setContentDescription("WLZ ClickGUI");

        final FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.LEFT | Gravity.TOP);
        p.leftMargin = prefs.getInt("circle_x", dp(18));
        p.topMargin = prefs.getInt("circle_y", dp(120));

        circle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = p.leftMargin;
                    startY = p.topMargin;
                    moved = false;
                    return true;
                }
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    int nx = startX + (int)(e.getRawX() - downX);
                    int ny = startY + (int)(e.getRawY() - downY);
                    nx = Math.max(0, Math.min(nx, root.getWidth() - v.getWidth()));
                    ny = Math.max(0, Math.min(ny, root.getHeight() - v.getHeight()));
                    p.leftMargin = nx;
                    p.topMargin = ny;
                    v.setLayoutParams(p);
                    prefs.edit().putInt("circle_x", nx).putInt("circle_y", ny).apply();
                    moved = true;
                    return true;
                }
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                    if (!moved) clickGui.setVisibility(clickGui.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    return true;
                }
                return true;
            }
        });

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
        panel.addView(fixLagCard(), top(7));

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
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), MC_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);

            root.postDelayed(new Runnable() {
                @Override public void run() {
                    try {
                        Intent hud = new Intent(MainActivity.this, WlzHudActivity.class);
                        hud.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION |
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        startActivity(hud);
                    } catch (Throwable ignored) {
                    }
                }
            }, 900L);
        } catch (Throwable e) {
            Toast.makeText(this, "Không mở được Minecraft runtime.", Toast.LENGTH_LONG).show();
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
        TextView t = text("WLZ Client 0.7.1 • ARM64 • no overlay permission", 8, MUTED, false);
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

    private LinearLayout.LayoutParams centered(int w, int h) {
        return centered(w, h, 0);
    }

    private LinearLayout.LayoutParams centered(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(top);
        return p;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams top(int margin) {
        return top(margin, -1);
    }

    private LinearLayout.LayoutParams top(int margin, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height < 0 ? -2 : height);
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

    private android.graphics.drawable.Drawable makeLogo() {
        int size = dp(112);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        android.graphics.Paint circle = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        circle.setStyle(android.graphics.Paint.Style.FILL);
        circle.setColor(BG);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dp(3), circle);

        circle.setStyle(android.graphics.Paint.Style.STROKE);
        circle.setStrokeWidth(dp(3));
        circle.setColor(ORANGE);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - dp(3), circle);

        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(ORANGE);
        p.setTextSize(dp(27));
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
        float baseline = size / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("WLZ", size / 2f, baseline, p);
        return new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
    }

    private LinearLayout.LayoutParams topCentered(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(margin);
        return p;
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

        TextView hint = text("WLZ • module control + Fix Lag nằm trong nút tròn.", 9, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint, top(8));

        ScrollView scroll = new ScrollView(this);
        LinearLayout spacer = new LinearLayout(this);
        spacer.setOrientation(LinearLayout.VERTICAL);
        spacer.addView(runtimeInfo(), lp(-1, -2));
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

        TextView badge = text("0.7.1", 10, ORANGE_LIGHT, true);
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

        TextView sub = text("Minecraft Bedrock runtime tích hợp • ARM64 • Android 9+", 10, MUTED, false);
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
        final Button circle = new Button(this);
        circle.setText("WLZ");
        circle.setTextColor(TEXT);
        circle.setTextSize(12);
        circle.setAllCaps(false);
        circle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        circle.setBackground(circleBackground());
        circle.setContentDescription("WLZ ClickGUI");

        final FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.LEFT | Gravity.TOP);
        p.leftMargin = prefs.getInt("circle_x", dp(18));
        p.topMargin = prefs.getInt("circle_y", dp(120));

        circle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = p.leftMargin;
                    startY = p.topMargin;
                    moved = false;
                    return true;
                }
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    int nx = startX + (int)(e.getRawX() - downX);
                    int ny = startY + (int)(e.getRawY() - downY);
                    nx = Math.max(0, Math.min(nx, root.getWidth() - v.getWidth()));
                    ny = Math.max(0, Math.min(ny, root.getHeight() - v.getHeight()));
                    p.leftMargin = nx;
                    p.topMargin = ny;
                    v.setLayoutParams(p);
                    prefs.edit().putInt("circle_x", nx).putInt("circle_y", ny).apply();
                    moved = true;
                    return true;
                }
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                    if (!moved) clickGui.setVisibility(clickGui.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    return true;
                }
                return true;
            }
        });

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
        panel.addView(fixLagCard(), top(7));

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
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), MC_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);

            root.postDelayed(new Runnable() {
                @Override public void run() {
                    try {
                        Intent hud = new Intent(MainActivity.this, WlzHudActivity.class);
                        hud.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION |
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        startActivity(hud);
                    } catch (Throwable ignored) {
                    }
                }
            }, 900L);
        } catch (Throwable e) {
            Toast.makeText(this, "Không mở được Minecraft runtime.", Toast.LENGTH_LONG).show();
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
        TextView t = text("WLZ Client 0.7.1 • ARM64 • no overlay permission", 8, MUTED, false);
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

    private LinearLayout.LayoutParams centered(int w, int h) {
        return centered(w, h, 0);
    }

    private LinearLayout.LayoutParams centered(int w, int h, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(top);
        return p;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams top(int margin) {
        return top(margin, -1);
    }

    private LinearLayout.LayoutParams top(int margin, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height < 0 ? -2 : height);
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
