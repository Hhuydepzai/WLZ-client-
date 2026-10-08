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
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private boolean radialVisible;

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
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(68), dp(68));
        cp.leftMargin = prefs().getInt("hud_x", dp(14));
        cp.topMargin = prefs().getInt("hud_y", dp(120));
        layer.addView(circle, cp);
        makeDraggable(circle, cp);

        if (WlzKeyMapper.areHotkeysEnabled(activity)) installKeyHook();
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

    public static void refreshAllHotkeys() {
        for (WlzInGameHud hud : ACTIVE.values()) {
            if (WlzKeyMapper.areHotkeysEnabled(hud.activity)) {
                hud.installKeyHook();
            } else {
                hud.restoreKeyHook();
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

        final int size = dp(250);
        radialMenu = new FrameLayout(activity);
        radialMenu.setBackground(circleBackground());

        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) circle.getLayoutParams();
        int cx = cp.leftMargin + circle.getWidth() / 2;
        int cy = cp.topMargin + circle.getHeight() / 2;

        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(size, size);
        rp.leftMargin = Math.max(0, Math.min(cx - size / 2, Math.max(0, decor.getWidth() - size)));
        rp.topMargin = Math.max(0, Math.min(cy - size / 2, Math.max(0, decor.getHeight() - size)));

        layer.addView(radialMenu, 0, rp);

        addRadialButton("ZOOM", 0, size / 2, dp(17), () -> toggleModule(0));
        addRadialButton("LOOK", 1, size - dp(42), size / 2 - dp(21), () -> toggleModule(1));
        addRadialButton("FPS", 3, size / 2 - dp(29), size - dp(54), () -> toggleModule(3));
        addRadialButton("BRIGHT", 4, dp(13), size / 2 - dp(24), () -> toggleModule(4));
        addRadialButton("MAP PHÍM", -2, size / 2 - dp(42), size - dp(91), this::openKeyMapper);
        addRadialButton(hotkeyLabel(), -3, dp(29), dp(24), this::toggleHotkeys);

        WlzLogoView center = makeCircle();
        center.setCompactCircle(true);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(dp(70), dp(70));
        centerLp.gravity = Gravity.CENTER;
        radialMenu.addView(center, centerLp);

        circle.bringToFront();
        center.setOnClickListener(v -> closeRadialMenu());
    }

    private void addRadialButton(String label, int action, int left, int top, final Runnable click) {
        Button b = button(label);
        b.setTextSize(action == -2 ? 8 : 8.5f);
        b.setBackground(circleButtonBackground());
        b.setOnClickListener(v -> {
            click.run();
            if (action >= 0) {
                refreshRadialLabels();
            }
        });

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(84), dp(44));
        p.leftMargin = left;
        p.topMargin = top;
        radialMenu.addView(b, p);
    }

    private void refreshRadialLabels() {
        if (radialMenu == null) return;
        for (int i = 0; i < radialMenu.getChildCount(); i++) {
            View v = radialMenu.getChildAt(i);
            if (!(v instanceof Button)) continue;
            Button b = (Button) v;
            String label = b.getText().toString();
            if ("ZOOM".equals(label)) b.setText(moduleLabel("ZOOM", 0));
            else if ("LOOK".equals(label)) b.setText(moduleLabel("LOOK", 1));
            else if ("FPS".equals(label)) b.setText(moduleLabel("FPS", 3));
            else if ("BRIGHT".equals(label)) b.setText(moduleLabel("BRIGHT", 4));
            else if (label.startsWith("HOTKEY")) b.setText(hotkeyLabel());
        }
    }

    private String moduleLabel(String name, int index) {
        return WlzModuleManager.isModuleEnabled(activity, index) ? name + " ✓" : name;
    }

    private String hotkeyLabel() {
        return WlzKeyMapper.areHotkeysEnabled(activity) ? "HOTKEY ✓" : "HOTKEY";
    }

    private void toggleModule(int index) {
        boolean enabled = WlzModuleManager.isModuleEnabled(activity, index);
        WlzModuleManager.setModuleEnabled(activity, index, !enabled);
        applyVisuals();
        if (radialMenu != null) refreshRadialLabels();
    }

    private void toggleHotkeys() {
        boolean enabled = WlzKeyMapper.areHotkeysEnabled(activity);
        WlzKeyMapper.setHotkeysEnabled(activity, !enabled);
        if (radialMenu != null) refreshRadialLabels();
    }

    private void openKeyMapper() {
        closeRadialMenu();
        activity.startActivity(new Intent(activity, WlzControlEditorActivity.class));
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
                            if (best == null || mode.getRefreshRate() > best.getRefreshRate()) {
                                best = mode;
                            }
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
