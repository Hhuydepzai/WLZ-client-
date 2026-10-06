package com.wlz.client;

import android.app.Activity;
import android.content.Intent;
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
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class WlzInGameHud {
    private static final Map<Activity, WlzInGameHud> ACTIVE = new HashMap<Activity, WlzInGameHud>();

    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int ORANGE_LIGHT = Color.rgb(255, 171, 76);
    private static final int BG = Color.rgb(10, 12, 16);
    private static final int ROW = Color.rgb(18, 21, 27);
    private static final int STROKE = Color.rgb(52, 59, 70);
    private static final int TEXT = Color.WHITE;
    private static final int MUTED = Color.rgb(150, 158, 170);

    private static final String[] NAMES = {
            "Zoom", "FreeLook", "Ném đồ", "Unlock FPS", "Fullbright",
            "Hitbox", "AutoSprint", "Snaplook", "FPS Counter"
    };
    private static final String[] SHORT = {"ZOOM", "LOOK", "DROP", "FPS", "BRIGHT", "HIT", "SPRINT", "SNAP", "FPS"};

    private final Activity activity;
    private final FrameLayout decor;
    private final FrameLayout layer;
    private final View circle;

    private TextView fpsText;
    private View brightLayer;
    private LinearLayout panel;
    private boolean panelVisible;

    private Window.Callback originalCallback;
    private Window.Callback callbackProxy;

    private long frameWindowStart = System.nanoTime();
    private int frameCount;

    private WlzInGameHud(Activity activity) {
        this.activity = activity;
        this.decor = (FrameLayout) activity.getWindow().getDecorView();
        this.layer = new FrameLayout(activity);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        decor.addView(layer, new FrameLayout.LayoutParams(-1, -1));

        circle = makeCircle();
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(62), dp(62));
        cp.leftMargin = prefs().getInt("hud_x", dp(14));
        cp.topMargin = prefs().getInt("hud_y", dp(120));
        layer.addView(circle, cp);
        makeDraggable(circle, cp, "hud_x", "hud_y", true);

        installKeyHook();
        applyVisuals();
    }

    public static void attach(Activity activity) {
        if (activity == null) return;
        if (!"com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName())) return;

        activity.runOnUiThread(() -> {
            if (!ACTIVE.containsKey(activity)) {
                ACTIVE.put(activity, new WlzInGameHud(activity));
            }
        });
    }

    public static void detach(Activity activity) {
        WlzInGameHud hud = ACTIVE.remove(activity);
        if (hud != null) hud.close();
    }

    private void installKeyHook() {
        try {
            Window window = activity.getWindow();
            originalCallback = window.getCallback();
            if (originalCallback == null) return;

            if (originalCallback.getClass().getName().contains("WlzInGameHud")) return;

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
                                        && !event.isLongPress()
                                        && WlzKeyMapper.trigger(activity, event.getKeyCode())) {
                                    applyVisuals();
                                    return true;
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
        b.setContentDescription("WLZ ClickGUI");
        return b;
    }

    private void togglePanel() {
        panelVisible = !panelVisible;
        if (panelVisible) {
            panel = buildPanel();
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(320), dp(500));
            pp.rightMargin = dp(10);
            pp.topMargin = dp(70);
            pp.gravity = Gravity.RIGHT | Gravity.TOP;
            layer.addView(panel, pp);
        } else if (panel != null) {
            layer.removeView(panel);
            panel = null;
        }
    }

    private LinearLayout buildPanel() {
        LinearLayout p = new LinearLayout(activity);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(10), dp(10), dp(10));
        p.setBackground(round(BG, ORANGE, 2, 18));

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("WLZ CLICKGUI", 16, ORANGE, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        Button map = button("MAP PHÍM");
        map.setOnClickListener(v -> activity.startActivity(
                new Intent(activity, WlzControlEditorActivity.class)));
        head.addView(map, lp(dp(94), dp(38)));

        Button close = button("ĐÓNG");
        close.setOnClickListener(v -> togglePanel());
        head.addView(close, lp(dp(72), dp(38)));
        p.addView(head);

        ScrollViewWithParams scroll = new ScrollViewWithParams(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < NAMES.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(7), dp(3), dp(4), dp(3));
            row.setBackground(round(ROW, STROKE, 1, 10));
            row.addView(text(NAMES[i], 10, TEXT, true), new LinearLayout.LayoutParams(0, dp(46), 1));

            Switch sw = new Switch(activity);
            sw.setChecked(WlzModuleManager.isModuleEnabled(activity, idx));
            sw.setOnCheckedChangeListener((b, enabled) -> {
                WlzModuleManager.setModuleEnabled(activity, idx, enabled);
                applyVisuals();
            });
            row.addView(sw, lp(-2, dp(46)));

            LinearLayout.LayoutParams rp = lp(-1, dp(51));
            rp.bottomMargin = dp(5);
            list.addView(row, rp);
        }

        LinearLayout fix = new LinearLayout(activity);
        fix.setOrientation(LinearLayout.VERTICAL);
        fix.setPadding(dp(8), dp(8), dp(8), dp(8));
        fix.setBackground(round(ROW, ORANGE, 1, 12));
        fix.addView(text("FIX LAG", 11, ORANGE_LIGHT, true));
        fix.addView(text("Hồ sơ: " + profile(), 9, MUTED, false), top(2));

        LinearLayout levels = new LinearLayout(activity);
        String[] labels = {"NHẸ", "MẠNH", "SIÊU"};
        for (int i = 1; i <= 3; i++) {
            final int level = i;
            Button b = button(labels[i - 1]);
            b.setOnClickListener(v -> applyFix(level));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 1) bp.leftMargin = dp(5);
            levels.addView(b, bp);
        }
        fix.addView(levels, top(6));
        list.addView(fix, top(5));

        scroll.addView(list);
        p.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        Button hide = button("ẨN WLZ");
        hide.setOnClickListener(v -> togglePanel());
        p.addView(hide, top(7));

        return p;
    }

    private void applyFix(int level) {
        level = Math.max(1, Math.min(3, level));
        boolean remove = prefs().getBoolean("fix_remove_effects", level >= 2);
        int colors = level >= 3 ? 3 : 4;
        prefs().edit()
                .putInt("lag_profile", level)
                .putBoolean("fix_remove_effects", remove)
                .putInt("texture_colors", colors)
                .apply();
        WlzModuleManager.setModuleEnabled(activity, 8, true);
        WlzModuleManager.setParam(activity, 3, level);
        WlzModuleManager.setParam(activity, 4, remove ? 1 : 0);
        WlzModuleManager.setParam(activity, 5, colors);
        applyVisuals();
        if (panel != null) {
            layer.removeView(panel);
            panel = null;
        }
        panelVisible = false;
    }

    private void applyVisuals() {
        boolean zoom = WlzModuleManager.isModuleEnabled(activity, 0);
        SurfaceView sv = findSurfaceView(decor);
        if (sv != null) {
            sv.setPivotX(sv.getWidth() / 2f);
            sv.setPivotY(sv.getHeight() / 2f);
            float scale = zoom ? 1.22f : 1.0f;
            sv.setScaleX(scale);
            sv.setScaleY(scale);
        }

        applyUnlockFps();

        boolean bright = WlzModuleManager.isModuleEnabled(activity, 4);
        if (bright) {
            if (brightLayer == null) {
                brightLayer = new View(activity);
                brightLayer.setBackgroundColor(Color.WHITE);
                brightLayer.setAlpha(0.10f);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -1);
                layer.addView(brightLayer, 0, bp);
            }
            brightLayer.setVisibility(View.VISIBLE);
        } else if (brightLayer != null) {
            brightLayer.setVisibility(View.GONE);
        }

        if (WlzModuleManager.isModuleEnabled(activity, 8)) startFps();
        else stopFps();

    }

    private void applyUnlockFps() {
        if (!WlzModuleManager.isModuleEnabled(activity, 3)) return;

        try {
            Window window = activity.getWindow();
            WindowManager.LayoutParams lp = window.getAttributes();

            if (Build.VERSION.SDK_INT >= 23) {
                Display display = activity.getWindowManager().getDefaultDisplay();
                if (display != null) {
                    if (Build.VERSION.SDK_INT >= 30) {
                        Display.Mode best = null;
                        for (Display.Mode mode : display.getSupportedModes()) {
                            if (best == null || mode.getRefreshRate() > best.getRefreshRate()) best = mode;
                        }
                        if (best != null) lp.preferredDisplayModeId = best.getModeId();
                    }
                    lp.preferredRefreshRate = Math.max(lp.preferredRefreshRate, maxRefreshRate(display));
                    window.setAttributes(lp);
                }
            }
        } catch (Throwable ignored) {
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

    private SurfaceView findSurfaceView(View v) {
        if (v instanceof SurfaceView) return (SurfaceView) v;
        if (!(v instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            SurfaceView s = findSurfaceView(g.getChildAt(i));
            if (s != null) return s;
        }
        return null;
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

    private void makeDraggable(View v, final FrameLayout.LayoutParams p, final String xKey, final String yKey, final boolean circleView) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
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
                    prefs().edit().putInt(xKey, p.leftMargin).putInt(yKey, p.topMargin).apply();
                    moved = true;
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!moved && circleView) togglePanel();
                    return true;
                }
                return true;
            }
        });
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences("wlz_settings", Activity.MODE_PRIVATE);
    }

    private SharedPreferences controlPrefs() {
        return activity.getSharedPreferences("wlz_controls", Activity.MODE_PRIVATE);
    }

    private String profile() {
        int p = prefs().getInt("lag_profile", 1);
        return p == 3 ? "SIÊU" : (p == 2 ? "MẠNH" : "NHẸ");
    }

    private void close() {
        stopFps();
        restoreKeyHook();
        try {
            decor.removeView(layer);
        } catch (Throwable ignored) {
        }
    }

    private Button button(String s) {
        Button b = new Button(activity);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(round(ROW, STROKE, 1, 10));
        return b;
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams top(int m) {
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(m);
        return p;
    }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(width), stroke);
        return d;
    }

    private GradientDrawable circleBg() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ORANGE);
        d.setStroke(dp(2), TEXT);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    private static final class ScrollViewWithParams extends android.widget.ScrollView {
        ScrollViewWithParams(Activity activity) {
            super(activity);
        }
    }
}
