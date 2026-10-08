package com.wlz.client;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class WlzInGameHud {
    private static final int ORANGE = Color.rgb(255,112,0);
    private static final int BG = Color.rgb(14,16,20);
    private static final int STROKE = Color.rgb(54,60,72);
    private static final int TEXT = Color.rgb(245,247,250);
    private static final int MUTED = Color.rgb(150,158,170);

    private static final String[] NAMES = {
        "Zoom","FreeLook","Ném đồ","Unlock FPS","Fullbright","Hitbox","AutoSprint","Snaplook","FPS Counter"
    };

    private static final java.util.WeakHashMap<Activity, FrameLayout> ROOTS =
        new java.util.WeakHashMap<>();

    private WlzInGameHud() {}

    public static void attach(Activity a) {
        if (a == null || ROOTS.containsKey(a)) return;
        FrameLayout content = a.findViewById(android.R.id.content);
        if (content == null) return;

        FrameLayout hud = new FrameLayout(a);
        hud.setTag(R.id.wlz_hud);

        Button circle = new Button(a);
        circle.setText("WLZ");
        circle.setTextColor(Color.WHITE);
        circle.setTextSize(10);
        circle.setAllCaps(false);
        circle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        circle.setBackground(circleBg());
        circle.setOnClickListener(v -> showPanel(a, content));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(a,64),dp(a,64));
        cp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        cp.rightMargin = dp(a,14);
        cp.topMargin = dp(a,18);
        hud.addView(circle, cp);

        content.addView(hud, new FrameLayout.LayoutParams(-1,-1));
        ROOTS.put(a, hud);
    }

    public static void detach(Activity a) {
        FrameLayout hud = ROOTS.remove(a);
        if (hud == null) return;
        try {
            View parent = hud.getParent();
            if (parent instanceof android.view.ViewGroup) ((android.view.ViewGroup)parent).removeView(hud);
        } catch (Throwable ignored) {}
    }

    private static void showPanel(Activity a, FrameLayout content) {
        final FrameLayout panel = new FrameLayout(a);
        panel.setBackgroundColor(0x66000000);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,10));
        card.setBackground(round(BG,ORANGE,2,18));

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(a,"WLZ  CLICKGUI",16,ORANGE,true);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(a,44),1));
        Button close = button(a,"ĐÓNG");
        head.addView(close,new LinearLayout.LayoutParams(dp(a,80),dp(a,44)));
        card.addView(head);

        ScrollView scroll = new ScrollView(a);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i=0;i<NAMES.length;i++) {
            final int idx=i;
            LinearLayout row=new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a,7),dp(a,3),dp(a,4),dp(a,3));
            row.setBackground(round(Color.rgb(19,22,28),STROKE,1,10));
            TextView n=text(a,NAMES[i],11,TEXT,true);
            row.addView(n,new LinearLayout.LayoutParams(0,dp(a,48),1));
            Switch sw=new Switch(a);
            sw.setChecked(WlzModuleManager.isModuleEnabled(a,idx));
            sw.setOnCheckedChangeListener((b,on)->WlzModuleManager.setModuleEnabled(a,idx,on));
            row.addView(sw,new LinearLayout.LayoutParams(-2,dp(a,48)));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(a,54));
            rp.bottomMargin=dp(a,5);
            list.addView(row,rp);
        }

        Button keymap=button(a,"MAP PHÍM / HOTKEY");
        keymap.setOnClickListener(v->Toast.makeText(a,"Dùng WlzKeyMapper để lưu hotkey trong cấu hình WLZ.",Toast.LENGTH_SHORT).show());
        list.addView(keymap,top(a,6));

        scroll.addView(list);
        card.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        close.setOnClickListener(v->content.removeView(panel));

        panel.addView(card,centerPanel(a));
        content.addView(panel,new FrameLayout.LayoutParams(-1,-1));
    }

    private static Button button(Activity a,String s){
        Button b=new Button(a); b.setText(s); b.setTextColor(TEXT); b.setTextSize(10); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackground(round(Color.rgb(20,23,29),STROKE,1,10)); return b;
    }
    private static TextView text(Activity a,String s,float size,int color,boolean bold){
        TextView t=new TextView(a); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    private static GradientDrawable circleBg(){
        GradientDrawable d=new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(ORANGE); d.setStroke(2,Color.WHITE); return d;
    }
    private static GradientDrawable round(int fill,int stroke,int width,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(radius); d.setStroke(width,stroke); return d;
    }
    private static FrameLayout.LayoutParams centerPanel(Activity a){
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(a,330),dp(a,540)); p.gravity=Gravity.CENTER; return p;
    }
    private static LinearLayout.LayoutParams top(Activity a,int m){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(a,m); return p;
    }
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
}
