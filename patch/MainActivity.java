package com.wlz.client;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 9, 12);
    private static final int PANEL = Color.rgb(19, 21, 26);
    private static final int PANEL_ALT = Color.rgb(27, 30, 36);
    private static final int STROKE = Color.rgb(48, 53, 63);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int TEXT = Color.rgb(246, 247, 250);
    private static final int MUTED = Color.rgb(151, 158, 169);
    private static final String[] MINECRAFT_PACKAGES = {
            "com.mojang.minecraftpe",
            "com.mojang.minecraftpe.preview"
    };

    private TextView installStatus;
    private TextView gameName;
    private Button playButton;
    private String installedPackage;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        buildScreen();
        refreshMinecraft();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshMinecraft();
    }

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(20), dp(22), dp(26));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("WLZ", 27, ORANGE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(PANEL_ALT, ORANGE, 1, 15));
        header.addView(mark, size(dp(62), dp(52)));

        LinearLayout branding = new LinearLayout(this);
        branding.setOrientation(LinearLayout.VERTICAL);
        branding.setPadding(dp(13), 0, 0, 0);
        branding.addView(text("WLZ CLIENT", 18, TEXT, true));
        branding.addView(text("MINECRAFT BEDROCK LAUNCHER", 9, MUTED, true), top(3, -2));
        header.addView(branding, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView version = text("2.5.0", 10, ORANGE, true);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(10), dp(7), dp(10), dp(7));
        version.setBackground(round(PANEL_ALT, STROKE, 1, 18));
        header.addView(version, size(-2, -2));
        page.addView(header);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(19), dp(18), dp(18));
        hero.setBackground(round(PANEL, Color.rgb(115, 61, 28), 1, 22));
        TextView eyebrow = text("YOUR GAME, ALREADY INSTALLED", 9, ORANGE, true);
        eyebrow.setLetterSpacing(0.08f);
        hero.addView(eyebrow);
        hero.addView(text("MỞ MINECRAFT\nTỪ WLZ", 28, TEXT, true), top(9, -2));
        hero.addView(text(
                "WLZ tìm Minecraft Bedrock có sẵn trên máy và mở trực tiếp. Không tải lại hay nhúng thêm game vào launcher.",
                11, MUTED, false), top(8, -2));

        installStatus = text("ĐANG KIỂM TRA MINECRAFT…", 10, MUTED, true);
        installStatus.setPadding(0, dp(14), 0, dp(3));
        hero.addView(installStatus);

        gameName = text("Chưa xác định bản game", 10, MUTED, false);
        hero.addView(gameName, top(3, -2));

        playButton = button("▶   MỞ MINECRAFT");
        playButton.setTextSize(14);
        playButton.setTextColor(Color.WHITE);
        playButton.setBackground(round(ORANGE, ORANGE, 1, 15));
        playButton.setEnabled(false);
        playButton.setOnClickListener(v -> launchMinecraft());
        hero.addView(playButton, top(16, dp(54)));
        page.addView(hero, top(22, -2));

        TextView metricsTitle = text("CHỈ SỐ TRONG GAME", 11, MUTED, true);
        metricsTitle.setLetterSpacing(0.08f);
        page.addView(metricsTitle, top(22, -2));

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metricCard("FPS", "--", "Khung hình/giây"), new LinearLayout.LayoutParams(0, dp(104), 1f));
        LinearLayout.LayoutParams metricGap = new LinearLayout.LayoutParams(0, dp(104), 1f);
        metricGap.leftMargin = dp(9);
        metrics.addView(metricCard("PING", "--", "Độ trễ máy chủ"), metricGap);
        LinearLayout.LayoutParams metricGap2 = new LinearLayout.LayoutParams(0, dp(104), 1f);
        metricGap2.leftMargin = dp(9);
        metrics.addView(metricCard("CPS", "--", "Lượt nhấp/giây"), metricGap2);
        page.addView(metrics, top(10, dp(104)));

        TextView bridgeNote = text(
                "HUD FPS / PING / CPS sẽ hoạt động khi có bridge chạy bên trong Minecraft. Launcher bên ngoài không thể tự đọc chính xác dữ liệu game hoặc thao tác chạm của game khác.",
                10, MUTED, false);
        bridgeNote.setLineSpacing(dp(3), 1f);
        page.addView(bridgeNote, top(10, -2));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(14), dp(13), dp(14), dp(13));
        footer.setBackground(round(PANEL, STROKE, 1, 16));
        footer.addView(text("KIẾN TRÚC LAUNCHER", 10, TEXT, true));
        footer.addView(text(
                "• Dùng bản Minecraft đã cài sẵn\n• WLZ không thay đổi dữ liệu thế giới\n• Không đóng gói lại assets hoặc thư viện Minecraft",
                10, MUTED, false), top(6, -2));
        Button rescan = button("QUÉT LẠI MINECRAFT");
        rescan.setTextColor(ORANGE);
        rescan.setBackground(round(PANEL_ALT, STROKE, 1, 12));
        rescan.setOnClickListener(v -> refreshMinecraft());
        footer.addView(rescan, top(12, dp(42)));
        page.addView(footer, top(18, -2));

        TextView bottom = text("WLZ CLIENT  •  BEDROCK", 9, Color.rgb(91, 97, 107), true);
        bottom.setGravity(Gravity.CENTER);
        page.addView(bottom, top(18, -2));
    }

    private View metricCard(String label, String value, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(round(PANEL, STROKE, 1, 15));
        TextView name = text(label, 9, ORANGE, true);
        name.setGravity(Gravity.CENTER);
        card.addView(name);
        TextView number = text(value, 22, TEXT, true);
        number.setGravity(Gravity.CENTER);
        card.addView(number, top(4, -2));
        TextView detail = text(description, 8, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        card.addView(detail, top(3, -2));
        return card;
    }

    private void refreshMinecraft() {
        installedPackage = null;
        String foundLabel = null;
        PackageManager pm = getPackageManager();

        for (String candidate : MINECRAFT_PACKAGES) {
            try {
                Intent launch = pm.getLaunchIntentForPackage(candidate);
                if (launch == null) continue;
                ApplicationInfo info = pm.getApplicationInfo(candidate, 0);
                installedPackage = candidate;
                foundLabel = pm.getApplicationLabel(info).toString();
                break;
            } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
                // Try the next known Bedrock package.
            } catch (Throwable ignored) {
                // An unexpected package-manager issue must not crash the launcher.
            }
        }

        boolean ready = installedPackage != null;
        installStatus.setText(ready ? "●  ĐÃ TÌM THẤY MINECRAFT" : "●  CHƯA TÌM THẤY MINECRAFT");
        installStatus.setTextColor(ready ? Color.rgb(104, 211, 139) : Color.rgb(255, 157, 105));
        gameName.setText(ready ? foundLabel + "  ·  " + installedPackage : "Cài Minecraft Bedrock rồi nhấn Quét lại");
        playButton.setEnabled(ready);
        playButton.setAlpha(ready ? 1f : 0.52f);
        playButton.setText(ready ? "▶   MỞ MINECRAFT" : "CHƯA CÓ MINECRAFT");
    }

    private void launchMinecraft() {
        if (installedPackage == null) {
            refreshMinecraft();
            Toast.makeText(this, "Chưa tìm thấy Minecraft Bedrock đã cài.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(installedPackage);
            if (launch == null) {
                Toast.makeText(this, "Minecraft không có màn hình khởi chạy khả dụng.", Toast.LENGTH_LONG).show();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        } catch (Throwable error) {
            Toast.makeText(this, "Không thể mở Minecraft: " + error.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
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

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(width), stroke);
        return d;
    }

    private LinearLayout.LayoutParams top(int margin, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height);
        if (margin > 0) p.topMargin = dp(margin);
        return p;
    }

    private LinearLayout.LayoutParams size(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
