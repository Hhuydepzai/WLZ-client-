package com.wlz.client;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public final class WlzInGameHud {
    private static final Map<Activity, WlzInGameHud> ACTIVE = new HashMap<Activity, WlzInGameHud>();
    private final Activity activity;
    private final FrameLayout decor;
    private final FrameLayout layer;
    private final Button circle;
    private TextView fpsText;
    private View brightLayer;
    private boolean panelVisible;
    private long frameWindowStart = System.nanoTime();
    private int frameCount;

    private WlzInGameHud(Activity activity) {
        this.activity = activity;
        this.decor = (FrameLayout) activity.getWindow().getDecorView();
        this.layer = new FrameLayout(activity);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);

        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(-1, -1);
        decor.addView(layer, full);

        circle = new Button(activity);
        circle.setText("WLZ");
        circle.setTextColor(Color.WHITE);
        circle.setTextSize(11);
        circle.setAllCaps(false);
        circle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        circle.setBackground(circleBg());
        circle.setContentDescription("WLZ ClickGUI");

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(62), dp(62));
        cp.leftMargin = prefs().getInt("hud_x", dp(14));
        cp.topMargin = prefs().getInt("hud_y", dp(120));
        layer.addView(circle, cp);
        makeDraggable(circle, cp);

        circle.setOnClickListener(v -> togglePanel());
        applyVisuals();
    }

    public static void attach(Activity activity) {
        if (!"com.mojang.minecraftpe.MainActivity".equals(activity.getClass().getName())) return;
        activity.runOnUiThread(() -> {
            if (!ACTIVE.containsKey(activity)) ACTIVE.put(activity, new WlzInGameHud(activity));
        });
    }

    public static void detach(Activity activity) {
        WlzInGameHud hud = ACTIVE.remove(activity);
        if (hud != null) hud.close();
    }

    private void togglePanel() {
        panelVisible = !panelVisible;
        if (panelVisible) {
            if (panel != null) layer.removeView(panel);
            panel = buildPanel();
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(320), dp(500));
            pp.leftMargin = Math.max(dp(8), activity.getResources().getDisplayMetrics().widthPixels - dp(330));
            pp.topMargin = dp(72);
            layer.addView(panel, pp);
        } else if (panel != null) {
            layer.removeView(panel);
            panel = null;
        }
    }

    private LinearLayout panel;

    private LinearLayout buildPanel() {
        LinearLayout p = new LinearLayout(activity);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(10), dp(10), dp(10));
        p.setBackground(round(Color.rgb(10,12,16), Color.rgb(255,112,0), 2, 18));

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("WLZ CLICKGUI", 16, Color.rgb(255,112,0), true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button("ĐÓNG");
        close.setOnClickListener(v -> togglePanel());
        head.addView(close, lp(dp(82), dp(38)));
        p.addView(head);

        String[] names = {"Zoom","FreeLook","Ném đồ","Unlock FPS","Fullbright","Hitbox","AutoSprint","Snaplook","FPS Counter"};
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(7), dp(3), dp(4), dp(3));
            row.setBackground(round(Color.rgb(18,21,27), Color.rgb(52,59,70), 1, 10));
            row.addView(text(names[i], 10, Color.WHITE, true),
                    new LinearLayout.LayoutParams(0, dp(46), 1));
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
        p.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        Button fix = button("FIX LAG • " + profile());
        fix.setOnClickListener(v -> {
            int level = prefs().getInt("lag_profile", 1) % 3 + 1;
            prefs().edit().putInt("lag_profile", level).apply();
            WlzModuleManager.setParam(activity, 3, level);
            fix.setText("FIX LAG • " + profile());
        });
        p.addView(fix, top(dp(6), dp(40)));
        return p;
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
        @Override public void doFrame(long frameTimeNanos) {
            frameCount++;
            long now = System.nanoTime();
            if (now - frameWindowStart >= 1_000_000_000L) {
                final int fps = frameCount;
                frameCount = 0;
                frameWindowStart = now;
                if (fpsText != null) activity.runOnUiThread(() -> fpsText.setText("FPS " + fps));
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void startFps() {
        if (fpsText == null) {
            fpsText = text("FPS 0", 10, Color.WHITE, true);
            fpsText.setBackground(round(Color.argb(170,10,12,16), Color.rgb(255,112,0), 1, 9));
            fpsText.setPadding(dp(7), dp(5), dp(7), dp(5));
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, -2);
            fp.leftMargin = dp(14); fp.topMargin = dp(188);
            layer.addView(fpsText, fp);
            frameCount = 0;
            frameWindowStart = System.nanoTime();
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }
    }

    private void stopFps() {
        if (fpsText != null) {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            layer.removeView(fpsText);
            fpsText = null;
        }
    }

    private void close() {
        stopFps();
        decor.removeView(layer);
    }

    private void makeDraggable(View v, final FrameLayout.LayoutParams p) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int sx, sy; boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX=e.getRawX(); downY=e.getRawY(); sx=p.leftMargin; sy=p.topMargin; moved=false; return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int nx=sx+(int)(e.getRawX()-downX), ny=sy+(int)(e.getRawY()-downY);
                    p.leftMargin=Math.max(0,Math.min(nx,decor.getWidth()-view.getWidth()));
                    p.topMargin=Math.max(0,Math.min(ny,decor.getHeight()-view.getHeight()));
                    view.setLayoutParams(p);
                    prefs().edit().putInt("hud_x",p.leftMargin).putInt("hud_y",p.topMargin).apply();
                    moved=true; return true;
                }
                if (e.getActionMasked()==MotionEvent.ACTION_UP) {
                    if (!moved) view.performClick();
                    return true;
                }
                return true;
            }
        });
    }

    private android.content.SharedPreferences prefs() {
        return activity.getSharedPreferences("wlz_settings", 0);
    }
    private String profile() {
        int p=prefs().getInt("lag_profile",1);
        return p==3?"SIÊU":p==2?"MẠNH":"NHẸ";
    }
    private Button button(String s) {
        Button b=new Button(activity); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(10);
        b.setAllCaps(false); b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD); b.setPadding(dp(6),0,dp(6),0);
        b.setBackground(round(Color.rgb(18,21,27),Color.rgb(52,59,70),1,10)); return b;
    }
    private TextView text(String s,float size,int color,boolean bold) {
        TextView t=new TextView(activity); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.MONOSPACE,Typeface.BOLD); return t;
    }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private LinearLayout.LayoutParams top(int m,int h){LinearLayout.LayoutParams p=lp(-1,h);p.topMargin=m;return p;}
    private GradientDrawable round(int fill,int stroke,int width,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(width),stroke); return d;
    }
    private GradientDrawable circleBg(){
        GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(Color.rgb(255,112,0)); d.setStroke(dp(2),Color.WHITE); return d;
    }
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
}
