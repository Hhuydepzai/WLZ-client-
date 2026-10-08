package com.wlz.client;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class WlzApplication extends com.zihao_il.MinecraftApplication {
    private static final String MC = "com.mojang.minecraftpe.MainActivity";

    private final ActivityLifecycleCallbacks callbacks = new ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(final Activity a, Bundle state) {
            if (!isMinecraft(a)) return;
            a.getWindow().getDecorView().postDelayed(() -> {
                if (!a.isFinishing() && !a.isDestroyed()) showLauncher(a);
            }, 250);
        }

        @Override public void onActivityResumed(final Activity a) {
            if (!isMinecraft(a)) return;
            a.getWindow().getDecorView().postDelayed(() -> {
                if (!a.isFinishing() && !a.isDestroyed()) {
                    try { WlzInGameHud.attach(a); } catch (Throwable ignored) {}
                }
            }, 1200);
        }

        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityPaused(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, Bundle s) {}
        @Override public void onActivityDestroyed(Activity a) {
            try { WlzInGameHud.detach(a); } catch (Throwable ignored) {}
        }
    };

    @Override public void onCreate() {
        // Keep Minecraft's original Application bootstrap intact.
        super.onCreate();
        registerActivityLifecycleCallbacks(callbacks);
    }

    private boolean isMinecraft(Activity a) {
        return a != null && MC.equals(a.getClass().getName());
    }

    private void showLauncher(Activity a) {
        final FrameLayout content = a.findViewById(android.R.id.content);
        if (content == null || content.getTag(R.id.wlz_launcher) != null) return;

        FrameLayout overlay = new FrameLayout(a);
        overlay.setBackgroundColor(Color.WHITE);
        overlay.setTag(R.id.wlz_launcher);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(a, 24), dp(a, 22), dp(a, 24), dp(a, 18));
        card.setBackground(round(Color.WHITE, Color.rgb(230,230,234), 1, 22));

        WlzLogoView logo = new WlzLogoView(a);
        logo.setCompactCircle(false);
        card.addView(logo, centered(180, 140, a));

        TextView title = text("WLZ CLIENT", 24, Color.rgb(255,112,0), true, a);
        title.setGravity(Gravity.CENTER);
        card.addView(title, top(7, a));

        TextView sub = text("BEDROCK • ARM64", 9, Color.rgb(115,118,124), true, a);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub, top(2, a));

        Button enter = new Button(a);
        enter.setText("▶  VÀO MINECRAFT");
        enter.setTextColor(Color.WHITE);
        enter.setTextSize(12);
        enter.setAllCaps(false);
        enter.setBackground(round(Color.rgb(255,112,0), Color.rgb(255,112,0), 1, 16));
        enter.setOnClickListener(v -> content.removeView(overlay));
        card.addView(enter, topButton(54, 16, a));

        overlay.addView(card, centeredFrame(390, 350, a));
        content.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private TextView text(String s,float size,int color,boolean bold,Activity a) {
        TextView t=new TextView(a); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }
    private LinearLayout.LayoutParams centered(int w,int h,Activity a){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(a,w),dp(a,h));
        p.gravity=Gravity.CENTER_HORIZONTAL; return p;
    }
    private FrameLayout.LayoutParams centeredFrame(int w,int h,Activity a){
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(a,w),dp(a,h));
        p.gravity=Gravity.CENTER; return p;
    }
    private LinearLayout.LayoutParams top(int m,Activity a){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(a,m); return p;
    }
    private LinearLayout.LayoutParams topButton(int h,int m,Activity a){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(a,h)); p.topMargin=dp(a,m); return p;
    }
    private android.graphics.drawable.GradientDrawable round(int fill,int stroke,int width,int radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(width),stroke); return d;
    }
    private int dp(Activity a,int v){ return Math.round(v*a.getResources().getDisplayMetrics().density); }
}
