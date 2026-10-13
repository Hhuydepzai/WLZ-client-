package com.wlz.client;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String MC_ACTIVITY = "com.mojang.minecraftpe.MainActivity";
    private static final int REQUEST_IMPORT_FIXLAG_PACKS = 2606;
    private static final int BG = Color.rgb(8, 9, 12);
    private static final int PANEL = Color.rgb(18, 20, 25);
    private static final int PANEL_2 = Color.rgb(24, 27, 33);
    private static final int STROKE = Color.rgb(48, 52, 61);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int TEXT = Color.rgb(246, 247, 250);
    private static final int MUTED = Color.rgb(150, 156, 166);
    private TextView runtimeStatus;
    private Button playButton;

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
        gridA.addView(featureCard("FIX LAG", "Ba cấu hình hiệu năng", "01"),
                new LinearLayout.LayoutParams(0, dp(92), 1f));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, dp(92), 1f);
        gap.leftMargin = dp(10);
        gridA.addView(featureCard("FUNCTIONS", "Chức năng trong game", "02"), gap);
        page.addView(gridA, top(10, dp(92)));

        LinearLayout gridB = new LinearLayout(this);
        gridB.setOrientation(LinearLayout.HORIZONTAL);
        gridB.addView(featureCard("CONTROLS", "Map phím và vị trí nút", "03"),
                new LinearLayout.LayoutParams(0, dp(92), 1f));
        LinearLayout.LayoutParams infoGap = new LinearLayout.LayoutParams(0, dp(92), 1f);
        infoGap.leftMargin = dp(10);
        gridB.addView(featureCard("RUNTIME", "Trạng thái Minecraft", "04"), infoGap);
        page.addView(gridB, top(10, dp(92)));

        page.addView(buildQuickActions(), top(18, -2));

        TextView foot = text("WLZ CLIENT  •  BEDROCK ARM64", 9, Color.rgb(93, 98, 107), true);
        foot.setGravity(Gravity.CENTER);
        page.addView(foot, top(20, -2));
        TextView foot2 = text("Giao diện WLZ • Không sửa dữ liệu thế giới", 9, Color.rgb(81, 86, 95), false);
        foot2.setGravity(Gravity.CENTER);
        page.addView(foot2, top(5, -2));

        refreshRuntime();
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

        TextView version = text("V2.3.7", 9, ORANGE, true);
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

        TextView info = text(
                "Map phím luôn sẵn sàng. Bật chức năng sẽ dùng ngay phím đã gán, không cần bật Hotkeys riêng.",
                10, MUTED, false);
        info.setLineSpacing(dp(2), 1.0f);
        panel.addView(info, top(5, -2));

        Button map = button("MAP PHÍM & VỊ TRÍ NÚT");
        map.setTextColor(TEXT);
        map.setBackground(round(PANEL_2, STROKE, 1, 12));
        map.setOnClickListener(v -> openKeyMapper());
        panel.addView(map, top(12, dp(44)));

        Button functions = button("CHỨC NĂNG ĐANG HỖ TRỢ");
        functions.setTextColor(ORANGE);
        functions.setBackground(round(PANEL_2, STROKE, 1, 12));
        functions.setOnClickListener(v -> Toast.makeText(this,
                "Trong game, chạm nút tròn WLZ rồi mở tab Functions hoặc Controls.",
                Toast.LENGTH_LONG).show());
        panel.addView(functions, top(8, dp(44)));

        if (!WlzLagPackManager.isBundled(this)) {
            TextView packInfo = text(
                    "Cần nhập 4 pack Fix Lag một lần. Sau đó WLZ tự cài và áp dụng profile, không cần Global Resources.",
                    9, MUTED, false);
            packInfo.setLineSpacing(dp(2), 1f);
            panel.addView(packInfo, top(12, -2));

            Button importPacks = button("NHẬP 4 PACK FIX LAG (MỘT LẦN)");
            importPacks.setTextColor(TEXT);
            importPacks.setBackground(round(PANEL_2, ORANGE, 1, 12));
            importPacks.setOnClickListener(v -> chooseFixLagPacks());
            panel.addView(importPacks, top(7, dp(44)));
        }
        return panel;
    }

    private void chooseFixLagPacks() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_IMPORT_FIXLAG_PACKS);
        } catch (Throwable error) {
            Toast.makeText(this, "Không mở được trình chọn file.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_FIXLAG_PACKS || resultCode != RESULT_OK || data == null) return;

        ArrayList<Uri> selected = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) selected.add(uri);
            }
        } else if (data.getData() != null) {
            selected.add(data.getData());
        }

        int imported = 0;
        for (Uri uri : selected) {
            String displayName = getDisplayName(uri);
            String canonical = canonicalFixLagName(displayName);
            if (canonical == null) continue;
            try {
                InputStream stream = getContentResolver().openInputStream(uri);
                WlzLagPackManager.importPack(this, canonical, stream);
                imported++;
            } catch (Throwable error) {
                Toast.makeText(this, "Không nhập được " + displayName, Toast.LENGTH_LONG).show();
            }
        }

        if (WlzLagPackManager.isBundled(this)) {
            Toast.makeText(this,
                    "Đã lưu đủ 4 pack. Vào Minecraft → Fix Lag 1/2/3 để tự áp dụng.",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this,
                    "Đã nhập " + imported + " pack ở lượt này. Hãy chọn đủ 4 file: 2 pack chung, fixlag2.mcpack và fixlag3.mcpack.",
                    Toast.LENGTH_LONG).show();
        }
        recreate();
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) return cursor.getString(col);
            }
        } catch (Throwable ignored) {}
        String path = uri == null ? "" : uri.getLastPathSegment();
        return path == null ? "" : path;
    }

    private String canonicalFixLagName(String displayName) {
        if (displayName == null) return null;
        String name = displayName.trim().toLowerCase(Locale.ROOT);
        if (name.equals("fixlag2.mcpack")) return "fixlag2.mcpack";
        if (name.equals("fixlag3.mcpack")) return "fixlag3.mcpack";
        if (name.startsWith("fixlag chung cho 2 bản2")) return "common_b.mcpack";
        if (name.startsWith("fixlag chung cho 2 bản")) return "common_a.mcpack";
        return null;
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
