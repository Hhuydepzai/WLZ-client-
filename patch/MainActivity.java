package com.wlz.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String[] KNOWN_MINECRAFT_PACKAGES = {
            "com.mojang.minecraftpe",
            "com.mojang.minecraftpe.preview"
    };

    private static final int BG = Color.rgb(8, 9, 12);
    private static final int PANEL = Color.rgb(18, 20, 25);
    private static final int PANEL_2 = Color.rgb(24, 27, 33);
    private static final int STROKE = Color.rgb(48, 52, 61);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int TEXT = Color.rgb(246, 247, 250);
    private static final int MUTED = Color.rgb(150, 156, 166);

    private TextView runtimeStatus;
    private Button playButton;
    private List<MinecraftTarget> detectedGames = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            createMainScreen();
        } catch (Throwable error) {
            Log.e("WLZStartup", "Main launcher UI failed; showing safe screen", error);
            showSafeMode();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshRuntime();
    }

    private void createMainScreen() {
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
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(22), dp(22), dp(22), dp(20));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        page.addView(buildHeader(), top(-1, dp(60)));
        page.addView(buildHero(), top(22, -2));

        Button choose = button("CHỌN BẢN MINECRAFT");
        choose.setTextColor(ORANGE);
        choose.setBackground(round(PANEL_2, STROKE, 1, 14));
        choose.setOnClickListener(v -> showMinecraftChooser());
        page.addView(choose, top(12, dp(48)));

        TextView note = text(
                "WLZ chỉ mở Minecraft đã cài trên máy; không cài thêm bản game và không chèn mod vào ứng dụng bên ngoài.",
                9, MUTED, false);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(3), 1.0f);
        page.addView(note, top(16, -2));

        refreshRuntime();
    }

    private LinearLayout buildHeader() {
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

        TextView version = text("V2.5.5", 9, ORANGE, true);
        version.setGravity(Gravity.CENTER);
        version.setPadding(dp(11), dp(7), dp(11), dp(7));
        version.setBackground(round(PANEL_2, STROKE, 1, 20));
        row.addView(version, lp(-2, -2));
        return row;
    }

    private LinearLayout buildHero() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(18));

        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(38, 28, 22), Color.rgb(20, 20, 24), PANEL});
        shape.setCornerRadius(dp(22));
        shape.setStroke(dp(1), Color.rgb(115, 61, 28));
        card.setBackground(shape);

        TextView eyebrow = text("YOUR WORLD. YOUR WAY.", 9, ORANGE, true);
        eyebrow.setLetterSpacing(0.10f);
        card.addView(eyebrow);

        TextView headline = text("SẴN SÀNG\nVÀO GAME?", 28, TEXT, true);
        headline.setLineSpacing(0f, 0.96f);
        card.addView(headline, top(8, -2));

        TextView description = text(
                "Mở bản Minecraft Bedrock đang có trên thiết bị của bạn.",
                11, MUTED, false);
        description.setLineSpacing(dp(3), 1.0f);
        card.addView(description, top(8, -2));

        runtimeStatus = text("ĐANG KIỂM TRA…", 9, MUTED, true);
        runtimeStatus.setPadding(0, dp(10), 0, 0);
        card.addView(runtimeStatus, top(4, -2));

        playButton = button("MỞ MINECRAFT");
        playButton.setTextSize(13);
        playButton.setTextColor(Color.WHITE);
        playButton.setBackground(round(ORANGE, ORANGE, 1, 14));
        playButton.setOnClickListener(v -> {
            if (findInstalledMinecraftApps().isEmpty()) showAllAppsChooser();
            else launchMinecraft();
        });
        card.addView(playButton, top(14, dp(54)));

        return card;
    }

    private void refreshRuntime() {
        detectedGames = findInstalledMinecraftApps();
        boolean found = !detectedGames.isEmpty();

        if (runtimeStatus != null) {
            if (found) {
                runtimeStatus.setText("●  ĐÃ TÌM THẤY MINECRAFT");
                runtimeStatus.setTextColor(Color.rgb(106, 208, 138));
            } else {
                runtimeStatus.setText("●  CHƯA NHẬN DIỆN · CHỌN THỦ CÔNG");
                runtimeStatus.setTextColor(Color.rgb(255, 153, 102));
            }
        }
        if (playButton != null) {
            playButton.setEnabled(true);
            playButton.setAlpha(1.0f);
            playButton.setText(found ? "▶  MỞ MINECRAFT ĐÃ CÀI" : "CHỌN ỨNG DỤNG ĐÃ CÀI");
        }
    }

    /**
     * Detect both the usual Bedrock package IDs and any visible launcher app
     * whose package name contains "com.mojang" or whose name/label identifies Minecraft. The manifest
     * declares a MAIN/LAUNCHER query so Android 11+ package visibility applies.
     */
    private List<MinecraftTarget> findInstalledMinecraftApps() {
        LinkedHashMap<String, MinecraftTarget> found = new LinkedHashMap<>();
        PackageManager pm = getPackageManager();

        for (String packageName : KNOWN_MINECRAFT_PACKAGES) {
            try {
                Intent launch = pm.getLaunchIntentForPackage(packageName);
                if (launch != null) {
                    String label = packageLabel(packageName);
                    ComponentName component = launch.getComponent();
                    found.put(packageName, new MinecraftTarget(label, packageName, component));
                }
            } catch (Throwable error) {
                Log.w("WLZRuntime", "Known Minecraft package query failed: " + packageName, error);
            }
        }

        Intent launcherQuery = new Intent(Intent.ACTION_MAIN);
        launcherQuery.addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            List<ResolveInfo> activities = pm.queryIntentActivities(
                    launcherQuery, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo info : activities) {
                if (info == null || info.activityInfo == null || !info.activityInfo.exported) continue;
                String packageName = info.activityInfo.packageName;
                if (packageName == null || packageName.isEmpty()) continue;
                String label;
                try {
                    CharSequence appLabel = info.loadLabel(pm);
                    label = appLabel == null ? packageName : appLabel.toString();
                } catch (Throwable ignored) {
                    label = packageName;
                }

                String p = packageName.toLowerCase(Locale.ROOT);
                String l = label.toLowerCase(Locale.ROOT);
                boolean looksLikeMinecraft =
                        p.contains("com.mojang") || p.contains("minecraft")
                                || l.contains("minecraft") || l.contains("bedrock")
                                || KNOWN_MINECRAFT_PACKAGES[0].equals(packageName)
                                || KNOWN_MINECRAFT_PACKAGES[1].equals(packageName);
                if (!looksLikeMinecraft) continue;

                ComponentName component = new ComponentName(packageName, info.activityInfo.name);
                found.putIfAbsent(packageName,
                        new MinecraftTarget(label, packageName, component));
            }
        } catch (Throwable error) {
            Log.w("WLZRuntime", "Launcher activity query failed", error);
        }

        return new ArrayList<>(found.values());
    }

    private String packageLabel(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null && label.length() > 0) return label.toString();
        } catch (Throwable ignored) {}
        return "Minecraft Bedrock";
    }

    private void launchMinecraft() {
        refreshRuntime();
        if (detectedGames.isEmpty()) {
            showAllAppsChooser();
            return;
        }
        if (detectedGames.size() > 1) {
            showMinecraftChooser();
            return;
        }
        launchTarget(detectedGames.get(0));
    }

    private void showMinecraftChooser() {
        refreshRuntime();
        if (detectedGames.isEmpty()) {
            showAllAppsChooser();
            return;
        }

        String[] labels = new String[detectedGames.size()];
        for (int i = 0; i < detectedGames.size(); i++) {
            MinecraftTarget target = detectedGames.get(i);
            labels[i] = target.label + "\n" + target.packageName;
        }
        new AlertDialog.Builder(this)
                .setTitle("CHỌN BẢN MINECRAFT")
                .setItems(labels, (dialog, which) -> launchTarget(detectedGames.get(which)))
                .setNegativeButton("HỦY", (dialog, which) -> dialog.dismiss())
                .show();
    }


    /** Fallback chooser. Uses a custom view instead of combining AlertDialog setMessage() + setItems(),
     * which can leave the list area blank on some Android builds. */
    private void showAllAppsChooser() {
        List<MinecraftTarget> apps = findAllLaunchableApps();

        // Even if package visibility returns no launcher activities, show explicit
        // candidates for the official Bedrock and Preview package IDs. The click
        // handler will attempt the normal launch intent, then the explicit activity.
        if (apps.isEmpty()) {
            apps.add(new MinecraftTarget(
                    "Minecraft Bedrock (thử mở)",
                    "com.mojang.minecraftpe",
                    new ComponentName("com.mojang.minecraftpe",
                            "com.mojang.minecraftpe.MainActivity")));
            apps.add(new MinecraftTarget(
                    "Minecraft Preview (thử mở)",
                    "com.mojang.minecraftpe.preview",
                    new ComponentName("com.mojang.minecraftpe.preview",
                            "com.mojang.minecraftpe.MainActivity")));
        }

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(6), dp(20), dp(8));

        TextView hint = text(
                "Chọn Minecraft đã cài. Nếu danh sách hệ thống trống, thử nút Bedrock bên dưới.",
                12, MUTED, false);
        hint.setLineSpacing(dp(2), 1.0f);
        body.addView(hint, lp(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(8), 0, dp(4));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("CHỌN GAME ĐÃ CÀI")
                .setNegativeButton("HỦY", (d, which) -> d.dismiss())
                .create();

        for (MinecraftTarget target : apps) {
            Button item = button(target.label + "\\n" + target.packageName);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setTextColor(TEXT);
            item.setTextSize(12);
            item.setPadding(dp(14), dp(6), dp(14), dp(6));
            item.setBackground(round(PANEL_2, STROKE, 1, 12));
            item.setMinHeight(dp(54));
            LinearLayout.LayoutParams itemParams = lp(-1, dp(58));
            itemParams.bottomMargin = dp(7);
            rows.addView(item, itemParams);
            item.setOnClickListener(v -> {
                dialog.dismiss();
                launchTarget(target);
            });
        }

        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        body.addView(scroll, top(6, dp(340)));
        dialog.setView(body);
        dialog.show();
    }

    private List<MinecraftTarget> findAllLaunchableApps() {
        LinkedHashMap<String, MinecraftTarget> apps = new LinkedHashMap<>();
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            List<ResolveInfo> activities = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo info : activities) {
                if (info == null || info.activityInfo == null || !info.activityInfo.exported) continue;
                String packageName = info.activityInfo.packageName;
                if (packageName == null || packageName.isEmpty() || packageName.equals(getPackageName())) continue;
                String label;
                try {
                    CharSequence appLabel = info.loadLabel(pm);
                    label = appLabel == null ? packageName : appLabel.toString();
                } catch (Throwable ignored) {
                    label = packageName;
                }
                ComponentName component = new ComponentName(packageName, info.activityInfo.name);
                apps.putIfAbsent(packageName, new MinecraftTarget(label, packageName, component));
            }
        } catch (Throwable error) {
            Log.e("WLZRuntime", "Could not list launchable apps", error);
        }
        List<MinecraftTarget> result = new ArrayList<>(apps.values());
        result.sort((a, b) -> {
            boolean am = looksLikeMinecraft(a);
            boolean bm = looksLikeMinecraft(b);
            if (am != bm) return am ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        return result;
    }

    private boolean looksLikeMinecraft(MinecraftTarget target) {
        String p = target.packageName.toLowerCase(Locale.ROOT);
        String l = target.label.toLowerCase(Locale.ROOT);
        return p.contains("minecraft") || p.contains("mojang") || p.contains("apollon")
                || l.contains("minecraft") || l.contains("bedrock") || l.contains("apollon");
    }

    private void launchTarget(MinecraftTarget target) {
        if (target == null || target.packageName == null || target.packageName.isEmpty()) {
            Toast.makeText(this, "Chưa chọn được ứng dụng Minecraft.", Toast.LENGTH_LONG).show();
            return;
        }

        PackageManager pm = getPackageManager();
        try {
            // Prefer Android's canonical launch intent. Some sideloaded Bedrock
            // builds expose an activity alias or require flags that a hand-built
            // MAIN/LAUNCHER intent may omit.
            Intent intent = pm.getLaunchIntentForPackage(target.packageName);

            // Fallback for renamed/side-loaded clients for which Android cannot
            // synthesize a package launch intent but did expose a launcher activity.
            if (intent == null && target.component != null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(target.component);
            }

            if (intent == null) {
                Log.w("WLZRuntime", "No launch intent for package " + target.packageName);
                Toast.makeText(this,
                        "Android không tìm thấy nút mở cho " + target.label
                                + ". Mở game một lần từ màn hình chính rồi thử lại.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Log.i("WLZRuntime", "Launching installed game package=" + target.packageName
                    + " component=" + intent.getComponent());
            startActivity(intent);
        } catch (SecurityException error) {
            Log.e("WLZRuntime", "Android denied launch for " + target.packageName, error);
            Toast.makeText(this,
                    "Android chặn việc mở " + target.label
                            + ". Kiểm tra game đã bật và không bị giới hạn bởi hệ thống.",
                    Toast.LENGTH_LONG).show();
        } catch (Throwable error) {
            Log.e("WLZRuntime", "Failed to open " + target.packageName, error);
            Toast.makeText(this,
                    "Không mở được " + target.label + " (" + target.packageName + "). "
                            + "Nếu vẫn lỗi, gửi ảnh thông báo này cho WLZ để dò đúng package.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showSafeMode() {
        try {
            Window window = getWindow();
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(BG);
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(BG);

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER_HORIZONTAL);
            panel.setPadding(dp(24), dp(24), dp(24), dp(24));

            TextView brand = text("WLZ CLIENT", 25, ORANGE, true);
            brand.setGravity(Gravity.CENTER);
            panel.addView(brand, top(0, -2));

            TextView detail = text("Không tải được màn hình chính. Hãy đóng app rồi mở lại.", 11, MUTED, false);
            detail.setGravity(Gravity.CENTER);
            panel.addView(detail, top(10, -2));

            Button retry = button("MỞ LẠI LAUNCHER");
            retry.setTextColor(Color.WHITE);
            retry.setBackground(round(ORANGE, ORANGE, 1, 14));
            retry.setOnClickListener(v -> recreate());
            panel.addView(retry, top(18, dp(50)));

            root.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            setContentView(root);
        } catch (Throwable fallbackError) {
            Log.e("WLZStartup", "Safe launcher screen also failed", fallbackError);
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

    private static final class MinecraftTarget {
        final String label;
        final String packageName;
        final ComponentName component;
        MinecraftTarget(String label, String packageName, ComponentName component) {
            this.label = label;
            this.packageName = packageName;
            this.component = component;
        }
    }
}
