package com.wlz.client;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Choreographer;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WlzInGameHud {
    private static final String HUD_TAG = "com.wlz.client.HUD_LAYER";
    private static final Map<Activity, WlzInGameHud> ACTIVE = new HashMap<Activity, WlzInGameHud>();

    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int ORANGE_LIGHT = Color.rgb(255, 171, 76);
    private static final int BG = Color.rgb(9, 10, 13);
    private static final int ROW = Color.rgb(22, 24, 29);
    private static final int STROKE = Color.rgb(55, 59, 67);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(155, 160, 170);

    private static final String[] NAMES = {
            "Zoom", "FreeLook", "Ném đồ", "Unlock FPS", "Fullbright",
            "Hitbox", "AutoSprint", "Snaplook", "FPS Counter"
    };

    private final Activity activity;
    private final FrameLayout decor;
    private final FrameLayout layer;
    private final WlzLogoView circle;

    private TextView fpsText;
    private FrameLayout radialMenu;
    private LinearLayout keyMapperPanel;
    private LinearLayout categoryNav;
    private FrameLayout categoryContent;
    private boolean radialVisible;
    private int selectedCategory = 0;
    private int captureAction = -1;

    private static final String[] GUI_CATEGORIES = {
            "Movement", "Combat", "Building", "Visuals",
            "Fix Lag", "Controls", "Other", "Info"
    };

    private Window.Callback originalCallback;
    private Window.Callback callbackProxy;

    private long frameWindowStart = System.nanoTime();
    private int frameCount;

    private WlzInGameHud(Activity activity) {
        this.activity = activity;
        this.decor = resolveDecor(activity);
        this.layer = new FrameLayout(activity);
        layer.setTag(HUD_TAG);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        decor.addView(layer, new FrameLayout.LayoutParams(-1, -1));

        circle = makeCircle();
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(68), dp(68));
        cp.leftMargin = prefs().getInt("hud_x", dp(14));
        cp.topMargin = prefs().getInt("hud_y", dp(120));
        layer.addView(circle, cp);
        makeDraggable(circle, cp);

        // Key mapping is always active; each bound key acts only when its
        // corresponding module is available in this runtime.
        installKeyHook();
        applyVisuals();
    }

    public static void attach(Activity activity) {
        if (activity == null) return;
        if (!"com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName())) return;

        activity.runOnUiThread(() -> {
            try {
                synchronized (ACTIVE) {
                    if (ACTIVE.containsKey(activity)) return;
                    View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
                    if (root instanceof ViewGroup && ((ViewGroup) root).findViewWithTag(HUD_TAG) != null) return;
                    // The base V6.6 screen contains a separate purple Apollon client panel.
                    // Remove it only if it is identifiable by its own title plus multiple
                    // legacy module labels in a bounded child view. Never remove game root.
                    removeLegacyApollonOverlay(root);
                    ACTIVE.put(activity, new WlzInGameHud(activity));
                }
            } catch (Throwable error) {
                Log.e("WLZRuntime", "HUD attach failed; continuing without overlay", error);
            }
        });
    }

    private static void removeLegacyApollonOverlay(View root) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup decor = (ViewGroup) root;
        View candidate = findLegacyOverlay(decor, decor);
        if (candidate == null || candidate == root || candidate.getId() == android.R.id.content) return;
        if (!(candidate.getParent() instanceof ViewGroup)) return;

        int rw = Math.max(1, decor.getWidth());
        int rh = Math.max(1, decor.getHeight());
        int cw = candidate.getWidth();
        int ch = candidate.getHeight();
        // A full-screen candidate could be the game's own content. Refuse to remove it.
        if (cw <= 0 || ch <= 0 || cw >= rw * 0.96f || ch >= rh * 0.96f) return;

        try {
            ((ViewGroup) candidate.getParent()).removeView(candidate);
            Log.i("WLZRuntime", "Removed detected legacy Apollon overlay view " +
                    candidate.getClass().getSimpleName() + " (" + cw + "x" + ch + ")");
        } catch (Throwable error) {
            Log.w("WLZRuntime", "Could not remove legacy overlay view", error);
        }
    }

    private static View findLegacyOverlay(ViewGroup parent, View decor) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            View nested = findLegacyOverlay((ViewGroup) child, decor);
            if (nested != null) return nested;

            LegacyMarkers markers = collectLegacyMarkers(child);
            if (!markers.hasApollonTitle || markers.modules.size() < 3) continue;
            if (child == decor || child.getId() == android.R.id.content) continue;

            int rw = Math.max(1, decor.getWidth());
            int rh = Math.max(1, decor.getHeight());
            if (child.getWidth() <= 0 || child.getHeight() <= 0) continue;
            if (child.getWidth() >= rw * 0.96f || child.getHeight() >= rh * 0.96f) continue;
            return child;
        }
        return null;
    }

    private static LegacyMarkers collectLegacyMarkers(View root) {
        LegacyMarkers markers = new LegacyMarkers();
        collectLegacyMarkersRecursive(root, markers);
        return markers;
    }

    private static void collectLegacyMarkersRecursive(View view, LegacyMarkers markers) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            String text = value == null ? "" : value.toString().toLowerCase();
            if (text.contains("apollon") && (text.contains("v6.6") || text.contains("chem") || text.contains("client"))) {
                markers.hasApollonTitle = true;
            }
            String[] moduleLabels = {
                    "freelook", "step", "ladder fly", "noclip", "killaura",
                    "target lock", "mobaura", "mob aura", "kill near items", "auto clicker"
            };
            for (String label : moduleLabels) {
                if (text.contains(label)) markers.modules.add(label);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectLegacyMarkersRecursive(group.getChildAt(i), markers);
            }
        }
    }

    private static final class LegacyMarkers {
        boolean hasApollonTitle;
        final java.util.Set<String> modules = new java.util.HashSet<String>();
        int moduleCount() { return modules.size(); }
    }

    private static FrameLayout resolveDecor(Activity activity) {
        View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (root instanceof FrameLayout) return (FrameLayout) root;
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof FrameLayout) return (FrameLayout) content;
        throw new IllegalStateException("No FrameLayout available for WLZ overlay");
    }

    public static void detach(Activity activity) {
        WlzInGameHud hud;
        synchronized (ACTIVE) {
            hud = ACTIVE.remove(activity);
        }
        if (hud != null) {
            try {
                hud.close();
            } catch (Throwable error) {
                Log.w("WLZRuntime", "HUD cleanup failed", error);
            }
        }
    }

    public static void refreshAllHotkeys() {
        final List<WlzInGameHud> snapshot;
        synchronized (ACTIVE) {
            snapshot = new ArrayList<WlzInGameHud>(ACTIVE.values());
        }
        for (WlzInGameHud hud : snapshot) {
            try {
                hud.installKeyHook();
            } catch (Throwable error) {
                Log.w("WLZRuntime", "Hotkey refresh failed", error);
            }
        }
    }

    private void installKeyHook() {
        try {
            Window window = activity.getWindow();
            if (callbackProxy != null && window.getCallback() == callbackProxy) return;

            originalCallback = window.getCallback();
            if (originalCallback == null) return;

            callbackProxy = (Window.Callback) Proxy.newProxyInstance(
                    Window.Callback.class.getClassLoader(),
                    new Class<?>[]{Window.Callback.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("dispatchKeyEvent".equals(method.getName())
                                    && args != null
                                    && args.length == 1
                                    && args[0] instanceof KeyEvent) {
                                KeyEvent event = (KeyEvent) args[0];
                                if (event.getAction() == KeyEvent.ACTION_DOWN
                                        && !event.isLongPress()) {
                                    if (captureAction >= 0) {
                                        int key = event.getKeyCode();
                                        WlzKeyMapper.setKey(activity, captureAction, key);
                                        captureAction = -1;
                                        rebuildKeyMapper();
                                        if (!WlzKeyMapper.areHotkeysEnabled(activity)) {
                                            restoreKeyHook();
                                        }
                                        return true;
                                    }
                                    if (WlzKeyMapper.trigger(activity, event.getKeyCode())) {
                                        applyVisuals();
                                        return true;
                                    }
                                }
                            }
                            return method.invoke(originalCallback, args);
                        }
                    });

            window.setCallback(callbackProxy);
        } catch (Throwable ignored) {
            callbackProxy = null;
        }
    }

    private void restoreKeyHook() {
        try {
            Window window = activity.getWindow();
            if (callbackProxy != null && window.getCallback() == callbackProxy) {
                window.setCallback(originalCallback);
            }
        } catch (Throwable ignored) {
        }
        callbackProxy = null;
    }

    private WlzLogoView makeCircle() {
        WlzLogoView b = new WlzLogoView(activity);
        b.setCompactCircle(true);
        b.setContentDescription("WLZ ClickGUI");
        b.setClickable(true);
        return b;
    }

    private void toggleRadialMenu() {
        if (radialVisible) {
            closeRadialMenu();
        } else {
            openRadialMenu();
        }
    }

    private void openRadialMenu() {
        closeRadialMenu();
        radialVisible = true;
        int availableW = Math.max(dp(320), decor.getWidth() - dp(24));
        int availableH = Math.max(dp(300), decor.getHeight() - dp(24));
        int width = Math.min(dp(820), availableW);
        int height = Math.min(dp(560), availableH);

        radialMenu = new FrameLayout(activity);
        radialMenu.setBackground(round(Color.argb(248, 10, 11, 15), ORANGE, 2, 16));
        radialMenu.setClipChildren(true);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(width, height);
        panelParams.gravity = Gravity.CENTER;
        layer.addView(radialMenu, panelParams);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), dp(10), dp(12), dp(12));
        radialMenu.addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("WLZ CLIENT  •  CLICKGUI", 15, ORANGE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button close = button("✕");
        close.setTextColor(ORANGE);
        close.setBackground(round(ROW, STROKE, 1, 10));
        close.setOnClickListener(v -> closeRadialMenu());
        header.addView(close, lp(dp(44), dp(40)));
        column.addView(header, top(0));

        TextView subtitle = text("Functions • Fix Lag • Controls", 9, MUTED, false);
        subtitle.setPadding(dp(2), dp(3), 0, dp(8));
        column.addView(subtitle);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.FILL);
        categoryNav = new LinearLayout(activity);
        categoryNav.setOrientation(LinearLayout.VERTICAL);
        categoryNav.setPadding(0, dp(2), dp(9), 0);
        body.addView(categoryNav, new LinearLayout.LayoutParams(dp(132), -1));

        View divider = new View(activity);
        divider.setBackgroundColor(STROKE);
        body.addView(divider, new LinearLayout.LayoutParams(dp(1), -1));

        categoryContent = new FrameLayout(activity);
        categoryContent.setPadding(dp(10), dp(2), dp(1), 0);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, -1, 1f);
        contentParams.leftMargin = dp(2);
        body.addView(categoryContent, contentParams);
        column.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        for (int i = 0; i < GUI_CATEGORIES.length; i++) {
            final int category = i;
            Button item = button(GUI_CATEGORIES[i]);
            item.setTag(Integer.valueOf(category));
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            item.setPadding(dp(10), 0, dp(4), 0);
            item.setOnClickListener(v -> {
                selectedCategory = category;
                refreshCategoryNavigation();
                renderCategoryContent();
            });
            LinearLayout.LayoutParams itemParams = lp(-1, dp(40));
            itemParams.bottomMargin = dp(4);
            categoryNav.addView(item, itemParams);
        }
        refreshCategoryNavigation();
        renderCategoryContent();
    }

    private void refreshCategoryNavigation() {
        if (categoryNav == null) return;
        for (int i = 0; i < categoryNav.getChildCount(); i++) {
            View view = categoryNav.getChildAt(i);
            if (!(view instanceof Button)) continue;
            Button item = (Button) view;
            int category = ((Integer) item.getTag()).intValue();
            boolean selected = category == selectedCategory;
            item.setTextColor(selected ? Color.WHITE : MUTED);
            item.setBackground(round(selected ? ORANGE : ROW,
                    selected ? ORANGE : STROKE, 1, 10));
        }
    }

    private void renderCategoryContent() {
        if (categoryContent == null) return;
        categoryContent.removeAllViews();
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(2), 0, dp(2), dp(3));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(page);
        categoryContent.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        addSectionHeading(page, GUI_CATEGORIES[selectedCategory].toUpperCase());
        switch (selectedCategory) {
            case 0:
                addModuleRow(page, 0, "Zoom", "Phóng to tầm nhìn trong game");
                addModuleRow(page, 1, "FreeLook", "Xoay camera mà không xoay người chơi");
                break;
            case 1:
                addInfoCard(page, "Chưa có module Combat được xác minh cho runtime này.",
                        "Không hiển thị công tắc giả cho KillAura hoặc Auto Clicker.");
                break;
            case 2:
                addInfoCard(page, "Chưa có module Building được port an toàn.",
                        "Phần này chỉ hiện chức năng khi đã được triển khai và kiểm tra.");
                break;
            case 3:
                addModuleRow(page, 3, "Unlock FPS", "Yêu cầu tần số quét cao nhất mà màn hình hỗ trợ");
                addModuleRow(page, 4, "Fullbright", "Tăng độ sáng nếu chữ ký runtime khớp");
                addModuleRow(page, 8, "FPS Counter", "Hiển thị bộ đếm khung hình trên màn hình");
                break;
            case 4:
                if (!WlzLagPackManager.isBundled(activity)) {
                    addInfoCard(page, "Pack Fix Lag chưa được nhúng trong APK này.",
                            "Tui không bật nút giả. Khi đủ 4 pack trong assets, Fix Lag 1/2/3 sẽ tự cài và kích hoạt trước khi mở game.");
                    addProfilePreview(page, 1, "Fix Lag 1", "Hai pack chung");
                    addProfilePreview(page, 2, "Fix Lag 2", "Hai pack chung + pack Fix Lag 2");
                    addProfilePreview(page, 3, "Fix Lag 3", "Hai pack chung + pack Fix Lag 3");
                } else {
                    addProfileButton(page, 1, "FIX LAG 1  •  LITE", "Hai pack chung");
                    addProfileButton(page, 2, "FIX LAG 2  •  BALANCED", "Hai pack chung + pack Fix Lag 2");
                    addProfileButton(page, 3, "FIX LAG 3  •  EXTREME", "Hai pack chung + pack Fix Lag 3");
                }
                break;
            case 5:
                addInfoCard(page, "Map phím tự động hoạt động cùng chức năng.",
                        "Không cần bật Hotkeys riêng. Chọn MAP để gán phím; phím của module không được hỗ trợ sẽ không bị chặn khỏi Minecraft.");
                Button map = button("MAP PHÍM & VỊ TRÍ NÚT");
                map.setTextColor(Color.WHITE);
                map.setBackground(round(ORANGE, ORANGE, 1, 12));
                map.setOnClickListener(v -> openKeyMapper());
                page.addView(map, top(10, dp(44)));
                break;
            case 6:
                addInfoCard(page, "Các module chưa được hỗ trợ trong bản runtime hiện tại",
                        "Ném đồ, Hitbox, AutoSprint và Snaplook chưa được xác minh. Chúng không được hiển thị như công tắc đang hoạt động.");
                break;
            default:
                addInfoCard(page, "WLZ Client • Bedrock ARM64",
                        "Bản ClickGUI được tách riêng thành Fix Lag, Functions và Controls. Chỉ bật module khi runtime báo hỗ trợ.");
                break;
        }
    }

    private void addSectionHeading(LinearLayout page, String label) {
        TextView heading = text(label, 11, ORANGE, true);
        heading.setLetterSpacing(0.06f);
        page.addView(heading, top(2));
    }

    private void addInfoCard(LinearLayout page, String title, String detail) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        card.setBackground(round(ROW, STROKE, 1, 12));
        card.addView(text(title, 11, TEXT, true));
        TextView body = text(detail, 9, MUTED, false);
        body.setLineSpacing(dp(2), 1f);
        card.addView(body, top(5));
        page.addView(card, top(8));
    }

    private void addModuleRow(LinearLayout page, final int index, String name, String detail) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(9), dp(7));
        row.setBackground(round(ROW, STROKE, 1, 12));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(name, 11, TEXT, true));
        TextView hint = text(detail, 9, MUTED, false);
        hint.setLineSpacing(dp(1), 1f);
        labels.addView(hint, top(3));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        boolean supported = WlzModuleManager.isModuleSupported(activity, index);
        if (supported) {
            Switch toggle = new Switch(activity);
            toggle.setChecked(WlzModuleManager.isModuleEnabled(activity, index));
            toggle.setOnCheckedChangeListener((buttonView, checked) -> {
                WlzModuleManager.setModuleEnabled(activity, index, checked);
                boolean actual = WlzModuleManager.isModuleEnabled(activity, index);
                if (actual != checked) buttonView.setChecked(actual);
                applyVisuals();
            });
            row.addView(toggle, lp(-2, -2));
        } else {
            TextView badge = text("CHƯA HỖ TRỢ", 8, MUTED, true);
            row.addView(badge, lp(-2, -2));
        }
        page.addView(row, top(8));
    }

    private void addProfilePreview(LinearLayout page, int profile, String title, String detail) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(round(ROW, STROKE, 1, 10));
        row.addView(text(title, 10, MUTED, true));
        row.addView(text(detail, 9, MUTED, false), top(2));
        page.addView(row, top(6));
    }

    private void addProfileButton(LinearLayout page, int profile, String title, String detail) {
        Button button = button(title + "  •  " + detail);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setTextColor(TEXT);
        button.setBackground(round(ROW, STROKE, 1, 10));
        button.setOnClickListener(v -> {
            boolean ok = WlzLagPackManager.applyPreset(activity, profile);
            if (ok) {
                Toast.makeText(activity, "Đã áp dụng Fix Lag " + profile + ". Mở lại thế giới để tải pack.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, "Không cài được pack Fix Lag; game chưa được đổi cấu hình.", Toast.LENGTH_LONG).show();
            }
        });
        page.addView(button, top(7, dp(44)));
    }

    private void toggleModule(int index) {
        if (!WlzModuleManager.isModuleSupported(activity, index)) {
            Toast.makeText(activity, "Module chưa được hỗ trợ trong runtime này.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean enabled = WlzModuleManager.isModuleEnabled(activity, index);
        WlzModuleManager.setModuleEnabled(activity, index, !enabled);
        applyVisuals();
        renderCategoryContent();
    }

    private void openKeyMapper() {
        closeRadialMenu();
        showKeyMapper();
    }

    private void showKeyMapper() {
        closeKeyMapper();
        keyMapperPanel = new LinearLayout(activity);
        keyMapperPanel.setOrientation(LinearLayout.VERTICAL);
        keyMapperPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        keyMapperPanel.setBackground(round(Color.WHITE, ORANGE, 2, 20));

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("WLZ • MAP PHÍM", 16, ORANGE, true);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView mappingStatus = text("HOTKEY TỰ ĐỘNG", 9, MUTED, true);
        head.addView(mappingStatus, lp(dp(116), dp(42)));

        Button close = button("X");
        close.setTextColor(ORANGE);
        close.setOnClickListener(v -> closeKeyMapper());
        head.addView(close, lp(dp(46), dp(42)));
        keyMapperPanel.addView(head);

        TextView info = text("Chọn MAP rồi bấm phím để gán. Hotkey hoạt động cùng module, không cần bật riêng.", 9, MUTED, false);
        keyMapperPanel.addView(info, top(7));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < NAMES.length; i++) {
            final int action = i;
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(5), dp(8), dp(5));
            row.setBackground(round(Color.rgb(247,247,249),
                    Color.rgb(225,225,229), 1, 12));

            row.addView(text(NAMES[i], 10, Color.rgb(35,35,38), true),
                    new LinearLayout.LayoutParams(0, dp(46), 1));

            Button key = button(WlzKeyMapper.keyName(
                    WlzKeyMapper.getKey(activity, action)));
            key.setTextColor(ORANGE);
            key.setOnClickListener(v -> {
                captureAction = action;
                if (!WlzKeyMapper.areHotkeysEnabled(activity)) {
                    installKeyHook();
                }
                rebuildKeyMapper();
            });
            row.addView(key, lp(dp(90), dp(42)));

            LinearLayout.LayoutParams rp = lp(-1, dp(51));
            rp.bottomMargin = dp(5);
            list.addView(row, rp);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);
        keyMapperPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                Math.min(dp(360), Math.max(dp(300), decor.getWidth() - dp(20))),
                Math.min(dp(520), Math.max(dp(360), decor.getHeight() - dp(40))));
        pp.gravity = Gravity.CENTER;
        layer.addView(keyMapperPanel, pp);
    }

    private void rebuildKeyMapper() {
        showKeyMapper();
    }

    private void closeKeyMapper() {
        if (keyMapperPanel != null) {
            layer.removeView(keyMapperPanel);
            keyMapperPanel = null;
        }
        captureAction = -1;
        if (!WlzKeyMapper.areHotkeysEnabled(activity)) restoreKeyHook();
    }

    private void closeRadialMenu() {
        if (radialMenu != null) {
            layer.removeView(radialMenu);
            radialMenu = null;
        }
        radialVisible = false;
    }

    private GradientDrawable circleBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.argb(235, 11, 12, 15));
        d.setStroke(dp(2), ORANGE);
        return d;
    }

    private GradientDrawable circleButtonBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.argb(220, 24, 25, 29));
        d.setStroke(dp(1), ORANGE);
        return d;
    }

    private void makeDraggable(View v, final FrameLayout.LayoutParams p) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            int startX;
            int startY;
            boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (radialVisible) closeRadialMenu();
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = p.leftMargin;
                    startY = p.topMargin;
                    moved = false;
                    return true;
                }

                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int nx = startX + (int) (e.getRawX() - downX);
                    int ny = startY + (int) (e.getRawY() - downY);
                    p.leftMargin = Math.max(0, Math.min(nx, decor.getWidth() - view.getWidth()));
                    p.topMargin = Math.max(0, Math.min(ny, decor.getHeight() - view.getHeight()));
                    view.setLayoutParams(p);
                    prefs().edit().putInt("hud_x", p.leftMargin).putInt("hud_y", p.topMargin).apply();
                    moved = true;
                    return true;
                }

                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!moved) toggleRadialMenu();
                    return true;
                }
                return true;
            }
        });
    }

    private void applyVisuals() {
        applyUnlockFps();
        if (WlzModuleManager.isModuleEnabled(activity, 8)) startFps();
        else stopFps();
    }

    private void applyUnlockFps() {
        try {
            Window window = activity.getWindow();
            WindowManager.LayoutParams lp = window.getAttributes();
            Display display = activity.getWindowManager().getDefaultDisplay();
            if (display == null) return;

            if (WlzModuleManager.isModuleEnabled(activity, 3)) {
                if (Build.VERSION.SDK_INT >= 30) {
                    Display.Mode best = null;
                    for (Display.Mode mode : display.getSupportedModes()) {
                        if (best == null || mode.getRefreshRate() > best.getRefreshRate()) best = mode;
                    }
                    if (best != null) lp.preferredDisplayModeId = best.getModeId();
                }
                lp.preferredRefreshRate = maxRefreshRate(display);
            } else {
                // Reset preferences when the switch is turned off rather than
                // leaving the last requested high refresh rate pinned.
                lp.preferredDisplayModeId = 0;
                lp.preferredRefreshRate = 0f;
            }
            window.setAttributes(lp);
        } catch (Throwable error) {
            Log.w("WLZRuntime", "Could not apply display refresh preference", error);
        }
    }

    private float maxRefreshRate(Display display) {
        float max = 60f;
        if (Build.VERSION.SDK_INT >= 23) {
            for (Display.Mode mode : display.getSupportedModes()) {
                max = Math.max(max, mode.getRefreshRate());
            }
        }
        try {
            max = Math.max(max, display.getRefreshRate());
        } catch (Throwable ignored) {
        }
        return max;
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frameCount++;
            long now = System.nanoTime();
            if (now - frameWindowStart >= 1_000_000_000L) {
                final int fps = frameCount;
                frameCount = 0;
                frameWindowStart = now;
                if (fpsText != null) {
                    activity.runOnUiThread(() -> fpsText.setText("FPS " + fps));
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void startFps() {
        if (fpsText != null) return;

        fpsText = text("FPS 0", 10, TEXT, true);
        fpsText.setBackground(round(Color.argb(170, 10, 12, 16), ORANGE, 1, 9));
        fpsText.setPadding(dp(7), dp(5), dp(7), dp(5));

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, -2);
        fp.leftMargin = dp(14);
        fp.topMargin = dp(244);
        layer.addView(fpsText, fp);

        frameCount = 0;
        frameWindowStart = System.nanoTime();
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stopFps() {
        if (fpsText == null) return;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        layer.removeView(fpsText);
        fpsText = null;
    }

    private Button button(String s) {
        Button b = new Button(activity);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(width), stroke);
        return d;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(margin);
        return p;
    }

    private LinearLayout.LayoutParams top(int margin, int height) {
        LinearLayout.LayoutParams p = lp(-1, height);
        p.topMargin = dp(margin);
        return p;
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences("wlz_settings", Activity.MODE_PRIVATE);
    }

    private String profile() {
        int p = prefs().getInt("lag_profile", 1);
        return p == 3 ? "SIÊU" : (p == 2 ? "MẠNH" : "NHẸ");
    }

    private void buildLegacyPanel() {
        // Intentionally unused. The radial menu replaced the large floating panel.
    }

    private void close() {
        stopFps();
        closeRadialMenu();
        restoreKeyHook();
        try {
            decor.removeView(layer);
        } catch (Throwable ignored) {
        }
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }
}
