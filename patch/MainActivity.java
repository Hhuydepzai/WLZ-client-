package com.wlz.client;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String MC_ACTIVITY = "com.mojang.minecraftpe.MainActivity";
    private static final int BG = Color.rgb(8, 9, 12);
    private static final int PANEL = Color.rgb(18, 20, 25);
    private static final int PANEL_2 = Color.rgb(24, 27, 33);
    private static final int STROKE = Color.rgb(48, 52, 61);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int TEXT = Color.rgb(246, 247, 250);
    private static final int MUTED = Color.rgb(150, 156, 166);
    private TextView runtimeStatus;
    private Button playButton;
    private Button hotkeyButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        FrameLayout root = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(15, 17, 22), BG, Color.rgb(5, 6, 8)});
        root.setBackground(background);
        setContentView(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(18), dp(22), dp(22));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        page.addView(buildHeader(), top(-1, dp(58)));
        page.addView(buildHero(), top(18, -2));

        TextView sectionTitle = text("TRUY CẬP NHANH", 11, MUTED, true);
        sectionTitle.setLetterSpacing(0.08f);
        page.addView(sectionTitle, top(23, -2));

        LinearLayout gridA = new LinearLayout(this);
        gridA.setOrientation(LinearLayout.HORIZONTAL);
        gridA.addView(featureCard("ZOOM", "Phóng to trong game", "01"),
                new LinearLayout.LayoutParams(0, dp(92), 1f));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, dp(92), 1f);
        gap.leftMargin = dp(10);
        gridA.addView(featureCard("FREELOOK", "Đổi góc nhìn", "02"), gap);
        page.addView(gridA, top(10, dp(92)));

        LinearLayout gridB = new LinearLayout(this);
        gridB.setOrientation(LinearLayout.HORIZONTAL);
        gridB.addView(featureCard("HOTKEYS", "Phím tắt trong game", "03"),
                new LinearLayout.LayoutParams(0, dp(92), 1f));
        LinearLayout.LayoutParams mapGap = new LinearLayout.LayoutParams(0, dp(92), 1f);
        mapGap.leftMargin = dp(10);
        gridB.addView(featureCard("MAP PHÍM", "Tùy chỉnh điều khiển", "04"), mapGap);
        page.addView(gridB, top(10, dp(92)));

        page.addView(buildQuickActions(), top(18, -2));

        TextView foot = text("WLZ CLIENT  •  BEDROCK ARM64", 9, Color.rgb(93, 98, 107), true);
        foot.setGravity(Gravity.CENTER);
        page.addView(foot, top(20, -2));
        TextView foot2 = text("Giao diện WLZ • Không sửa dữ liệu thế giới", 9, Color.rgb(81, 86, 95), false);
        foot2.setGravity(Gravity.CENTER);
        page.addView(foot2, top(5, -2));

        refreshRuntime();
        refreshHotkeyLabel();
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        WlzLogoView logo = new WlzLogoView(this);
        row.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);
        labels.addView(text("WLZ CLIENT", 18, TEXT, true), lp(-1, -2));
        labels.addView(text("MINECRAFT BEDROCK", 9, MUTED, true), top(2, -2));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView version = text("V2.3.5", 9, ORANGE, true);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(11), dp(7), dp(11), dp(7));
        version.setBackground(round(PANEL_2, STROKE, 1, 20));
        row.addView(version, lp(-2, -2));
        return row;
    }

    private View buildHero() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(38, 28, 22), Color.rgb(20, 20, 24), PANEL});
        shape.setCornerRadius(dp(22));
        shape.setStroke(dp(1), Color.rgb(115, 61, 28));
        card.setBackground(shape);

        TextView eyebrow = text("YOUR WORLD. YOUR WAY.", 9, ORANGE, true);
        eyebrow.setLetterSpacing(0.10f);
        card.addView(eyebrow);

        TextView headline = text("SẴN SÀNG\nVÀO GAME?", 27, TEXT, true);
        headline.setLineSpacing(0f, 0.94f);
        card.addView(headline, top(7, -2));

        TextView description = text(
                "Khởi chạy Minecraft và sử dụng nút WLZ tròn trong game để mở ClickGUI.",
                11, MUTED, false);
        description.setLineSpacing(dp(3), 1.0f);
        card.addView(description, top(7, -2));

        runtimeStatus = text("ĐANG KIỂM TRA GAME RUNTIME…", 9, MUTED, true);
        runtimeStatus.setPadding(0, dp(8), 0, 0);
        card.addView(runtimeStatus, top(5, -2));

        playButton = button("▶    CHƠI MINECRAFT");
        playButton.setTextSize(13);
        playButton.setTextColor(Color.WHITE);
        playButton.setBackground(round(ORANGE, ORANGE, 1, 14));
        playButton.setOnClickListener(v -> launchMinecraft());
        card.addView(playButton, top(14, dp(52)));

        return card;
    }

    private View featureCard(String title, String subtitle, String number) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(round(PANEL, STROKE, 1, 16));

        TextView topRow = text(number, 9, ORANGE, true);
        card.addView(topRow, lp(-1, -2));
        TextView label = text(title, 12, TEXT, true);
        label.setPadding(0, dp(4), 0, 0);
        card.addView(label, lp(-1, -2));
        TextView details = text(subtitle, 9, MUTED, false);
        details.setPadding(0, dp(2), 0, 0);
        card.addView(details, lp(-1, -2));
        return card;
    }

    private View buildQuickActions() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(round(PANEL, STROKE, 1, 18));

        TextView title = text("ĐIỀU KHIỂN WLZ", 11, TEXT, true);
        panel.addView(title);

        TextView info = text("Chỉnh keybind và hotkey tại đây. Overlay trong game vẫn mở bằng nút tròn WLZ.",
                10, MUTED, false);
        info.setLineSpacing(dp(2), 1.0f);
        panel.addView(info, top(5, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button map = button("MAP PHÍM");
        map.setTextColor(TEXT);
        map.setBackground(round(PANEL_2, STROKE, 1, 12));
        map.setOnClickListener(v -> openKeyMapper());
        actions.addView(map, new LinearLayout.LayoutParams(0, dp(44), 1f));

        hotkeyButton = button("HOTKEYS");
        hotkeyButton.setTextColor(ORANGE);
        hotkeyButton.setBackground(round(PANEL_2, STROKE, 1, 12));
        hotkeyButton.setOnClickListener(v -> {
            boolean enabled = WlzKeyMapper.areHotkeysEnabled(this);
            WlzKeyMapper.setHotkeysEnabled(this, !enabled);
            refreshHotkeyLabel();
        });
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        hp.leftMargin = dp(8);
        actions.addView(hotkeyButton, hp);
        panel.addView(actions, top(12, dp(44)));
        return panel;
    }

    private void launchMinecraft() {
        if (!hasMinecraftRuntime()) {
            refreshRuntime();
            Toast.makeText(this,
                    "Minecraft runtime không có trong APK này. Không thể mở game.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(getPackageName(), MC_ACTIVITY));
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (Throwable error) {
            runtimeStatus.setText("KHÔNG MỞ ĐƯỢC MINECRAFT");
            Toast.makeText(this,
                    "Không mở được Minecraft. Hãy kiểm tra log crash; WLZ không tự đóng app.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openKeyMapper() {
        try {
            startActivity(new Intent(this, WlzControlEditorActivity.class));
        } catch (Throwable error) {
            Toast.makeText(this, "Màn hình map phím chưa khả dụng.", Toast.LENGTH_LONG).show();
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

    private void refreshRuntime() {
        boolean ready = hasMinecraftRuntime();
        runtimeStatus.setText(ready ? "●  GAME RUNTIME ĐƯỢC PHÁT HIỆN" : "●  KHÔNG TÌM THẤY GAME RUNTIME");
        runtimeStatus.setTextColor(ready ? Color.rgb(106, 208, 138) : Color.rgb(255, 153, 102));
        if (playButton != null) playButton.setEnabled(ready);
        if (playButton != null && !ready) {
            playButton.setText("RUNTIME CHƯA SẴN SÀNG");
            playButton.setAlpha(0.58f);
        }
    }

    private void refreshHotkeyLabel() {
        if (hotkeyButton == null) return;
        boolean enabled = WlzKeyMapper.areHotkeysEnabled(this);
        hotkeyButton.setText(enabled ? "HOTKEYS  ✓" : "HOTKEYS  OFF");
        hotkeyButton.setTextColor(enabled ? Color.rgb(106, 208, 138) : ORANGE);
    }

    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(7), 0, dp(7), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
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

    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private LinearLayout.LayoutParams top(int margin) {
        return top(margin, -2);
    }

    private LinearLayout.LayoutParams top(int margin, int height) {
        LinearLayout.LayoutParams p = lp(-1, height);
        if (margin > 0) p.topMargin = dp(margin);
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
}
