package com.wlz.client;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class WlzApplication extends com.pairip.application.Application {
    private static final long NATIVE_ATTACH_DELAY_MS = 3000L;

    private final ActivityLifecycleCallbacks callbacks = new ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(Activity activity, Bundle state) {
            if (isMinecraft(activity)) showSplash(activity);
        }

        @Override public void onActivityStarted(Activity activity) {}

        @Override public void onActivityResumed(final Activity activity) {
            if (isMinecraft(activity)) {
                // Attach the native WLZ runtime only after Minecraft has had
                // time to initialize its own native engine.
                WlzInGameHud.attach(activity);
                activity.getWindow().getDecorView().postDelayed(new Runnable() {
                    @Override public void run() {
                        WlzModuleManager.initializeNative(activity);
                    }
                }, NATIVE_ATTACH_DELAY_MS);
            }
        }

        @Override public void onActivityPaused(Activity activity) {}

        @Override public void onActivityStopped(Activity activity) {}

        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

        @Override public void onActivityDestroyed(Activity activity) {
            if (isMinecraft(activity)) {
                WlzInGameHud.detach(activity);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Java/UI initialization is safe here. Native code is intentionally
        // deferred until the Minecraft Activity reaches RESUMED.
        WlzModuleManager.initialize(this);
        registerActivityLifecycleCallbacks(callbacks);
    }

    private static void showSplash(final Activity activity) {
        activity.getWindow().getDecorView().post(() -> {
            final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            final FrameLayout splash = new FrameLayout(activity);
            splash.setBackgroundColor(Color.rgb(7, 9, 12));

            LinearLayout box = new LinearLayout(activity);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(activity, 24), dp(activity, 24),
                    dp(activity, 24), dp(activity, 24));

            WlzLogoView logo = new WlzLogoView(activity);
            box.addView(logo, lp(activity, dp(activity, 118), dp(activity, 118)));

            TextView title = new TextView(activity);
            title.setText("WLZ CLIENT");
            title.setTextSize(21);
            title.setTextColor(Color.WHITE);
            title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
            tp.topMargin = dp(activity, 12);
            box.addView(title, tp);

            View line = new View(activity);
            line.setBackgroundColor(Color.rgb(255, 112, 0));
            box.addView(line, lp(activity, dp(activity, 82), dp(activity, 2),
                    dp(activity, 9)));

            TextView state = new TextView(activity);
            state.setText("ĐANG KHỞI ĐỘNG");
            state.setTextSize(9);
            state.setTextColor(Color.rgb(145, 154, 166));
            state.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            state.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.topMargin = dp(activity, 9);
            box.addView(state, sp);

            splash.addView(box, new FrameLayout.LayoutParams(-1, -1));
            decor.addView(splash, new ViewGroup.LayoutParams(-1, -1));

            splash.postDelayed(() -> {
                try { decor.removeView(splash); } catch (Throwable ignored) {}
            }, 900L);
        });
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams lp(Activity a, int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.gravity = Gravity.CENTER;
        return p;
    }

    private static LinearLayout.LayoutParams lp(Activity a, int w, int h, int topMargin) {
        LinearLayout.LayoutParams p = lp(a, w, h);
        p.topMargin = topMargin;
        return p;
    }

    private static boolean isMinecraft(Activity activity) {
        return activity != null
                && "com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName());
    }
}
