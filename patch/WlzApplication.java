package com.wlz.client;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

public final class WlzApplication extends com.zihao_il.MinecraftApplication {
    private static final long NATIVE_ATTACH_DELAY_MS = 3500L;
    private final Map<Activity, View> launchers = new WeakHashMap<Activity, View>();

    private final ActivityLifecycleCallbacks callbacks = new ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(Activity activity, Bundle state) {
            if (isMinecraft(activity)) {
                activity.getWindow().getDecorView().post(() -> showLauncher(activity));
            }
        }

        @Override public void onActivityStarted(Activity activity) {}

        @Override public void onActivityResumed(final Activity activity) {
            if (!isMinecraft(activity)) return;

            activity.getWindow().getDecorView().postDelayed(new Runnable() {
                @Override public void run() {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        try { WlzInGameHud.attach(activity); } catch (Throwable ignored) {}
                    }
                }
            }, NATIVE_ATTACH_DELAY_MS);
        }

        @Override public void onActivityPaused(Activity activity) {}

        @Override public void onActivityStopped(Activity activity) {}

        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

        @Override public void onActivityDestroyed(Activity activity) {
            if (isMinecraft(activity)) {
                removeLauncher(activity);
                try { WlzInGameHud.detach(activity); } catch (Throwable ignored) {}
            }
        }
    };

    @Override
    public void onCreate() {
        // Keep the original Minecraft application bootstrap untouched.
        super.onCreate();
        WlzModuleManager.initialize(this);
        registerActivityLifecycleCallbacks(callbacks);
    }

    private void showLauncher(final Activity activity) {
        if (launchers.containsKey(activity)) return;

        final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.WHITE);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(activity, 26), dp(activity, 24),
                dp(activity, 26), dp(activity, 22));
        card.setBackground(round(Color.WHITE, Color.rgb(232, 232, 236), 1, 24, activity));

        WlzLogoView logo = new WlzLogoView(activity);
        card.addView(logo, centered(180, 150, activity));

        TextView title = text("WLZ CLIENT", 25, Color.rgb(255, 112, 0), true, activity);
        title.setGravity(Gravity.CENTER);
        card.addView(title, top(8, activity));

        TextView sub = text("BEDROCK CLIENT", 10, Color.rgb(100, 103, 110), true, activity);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub, top(2, activity));

        TextView hint = text(
                "Minecraft đang khởi động…\n"
                        + "MAP PHÍM + HOTKEY nằm trong nút tròn WLZ.",
                9, Color.rgb(115, 118, 124), false, activity);
        hint.setGravity(Gravity.CENTER);
        card.addView(hint, top(12, activity));

        Button enter = new Button(activity);
        enter.setText("▶  VÀO MINECRAFT");
        enter.setTextColor(Color.WHITE);
        enter.setTextSize(12);
        enter.setAllCaps(false);
        enter.setTypeface(Typeface.DEFAULT_BOLD);
        enter.setBackground(round(Color.rgb(255, 112, 0),
                Color.rgb(255, 112, 0), 1, 16, activity));
        enter.setOnClickListener(v -> removeLauncher(activity));
        card.addView(enter, topButton(54, 15, activity));

        TextView foot = text("WLZ • ORANGE / WHITE", 8,
                Color.rgb(145, 148, 154), false, activity);
        foot.setGravity(Gravity.CENTER);
        card.addView(foot, top(11, activity));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                dp(activity, 390), dp(activity, 370));
        cp.gravity = Gravity.CENTER;
        overlay.addView(card, cp);

        decor.addView(overlay, new ViewGroup.LayoutParams(-1, -1));
        launchers.put(activity, overlay);
    }

    private void removeLauncher(Activity activity) {
        View v = launchers.remove(activity);
        if (v == null) return;
        try {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent != null) parent.removeView(v);
        } catch (Throwable ignored) {}
    }

    private static boolean isMinecraft(Activity activity) {
        return activity != null
                && "com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName());
    }

    private TextView text(String s, float size, int color, boolean bold, Activity a) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams centered(int w, int h, Activity a) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(dp(a, w), dp(a, h));
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    private LinearLayout.LayoutParams top(int margin, Activity a) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(a, margin);
        return p;
    }

    private LinearLayout.LayoutParams topButton(int h, int margin, Activity a) {
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, dp(a, h));
        p.topMargin = dp(a, margin);
        return p;
    }

    private GradientDrawable round(int fill, int stroke, int width,
                                   int radius, Activity a) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(a, radius));
        d.setStroke(dp(a, width), stroke);
        return d;
    }

    private int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}
