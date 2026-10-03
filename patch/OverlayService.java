package com.wlz.client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class OverlayService extends Service {
    private static final int PANEL=Color.rgb(18,27,23);
    private static final int PANEL2=Color.rgb(23,33,28);
    private static final int ORANGE=Color.rgb(239,112,18);
    private static final int TEXT=Color.rgb(241,239,231);
    private static final int MUTED=Color.rgb(164,171,163);
    private static final int MOSS=Color.rgb(100,119,66);
    private static final int MOSS_DARK=Color.rgb(48,63,43);
    private static final int GRAPHITE=Color.rgb(47,54,51);
    private static final String CH="wlz_overlay";
    private static final String[] N={"Zoom","FreeLook","Ném đồ","Unlock FPS","Fullbright","Hitbox","AutoSprint","Snaplook","FPS Counter"};

    private WindowManager wm;
    private WindowManager.LayoutParams bubbleLp,panelLp;
    private TextView bubble;
    private LinearLayout panel;
    private SharedPreferences prefs;

    @Override public void onCreate(){
        super.onCreate();
        prefs=getSharedPreferences("wlz_settings",MODE_PRIVATE);
        if(!Settings.canDrawOverlays(this)){stopSelf();return;}
        startForegroundCompat();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        createBubble();
        createPanel();
    }

    private void startForegroundCompat(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)
            nm.createNotificationChannel(new NotificationChannel(CH,"WLZ Overlay",NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),
                Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CH):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_manage)
         .setContentTitle("WLZ Client")
         .setContentText("Floating controls đang chạy")
         .setContentIntent(pi)
         .setOngoing(true);
        Notification n=b.build();
        if(Build.VERSION.SDK_INT>=34)
            startForeground(1104,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1104,n);
    }

    private void createBubble(){
        bubble=new TextView(this);
        bubble.setText("WLZ");
        bubble.setTextSize(12);
        bubble.setTextColor(TEXT);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        bubble.setBackground(circle(ORANGE,2));
        bubble.setElevation(dp(8));
        bubble.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY; int startX,startY; boolean moved;
            @Override public boolean onTouch(View v,MotionEvent e){
                if(e.getAction()==MotionEvent.ACTION_DOWN){
                    downX=e.getRawX(); downY=e.getRawY();
                    startX=bubbleLp.x; startY=bubbleLp.y; moved=false; return true;
                }
                if(e.getAction()==MotionEvent.ACTION_MOVE){
                    int nx=startX+(int)(e.getRawX()-downX);
                    int ny=startY+(int)(e.getRawY()-downY);
                    if(Math.abs(nx-startX)+Math.abs(ny-startY)>dp(8)) moved=true;
                    bubbleLp.x=Math.max(0,nx); bubbleLp.y=Math.max(0,ny);
                    try{wm.updateViewLayout(bubble,bubbleLp);}catch(Throwable ignored){}
                    return true;
                }
                if(e.getAction()==MotionEvent.ACTION_UP){
                    if(!moved) togglePanel();
                    return true;
                }
                return false;
            }
        });
        bubbleLp=overlayLp(56,56,20,220,false);
        add(bubble,bubbleLp);
    }

    private void createPanel(){
        panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10),dp(10),dp(10),dp(10));
        panel.setBackground(round(PANEL,ORANGE,1,16));

        LinearLayout head=new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("WLZ CLIENT",16,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        Button close=button("×");
        close.setOnClickListener(v->hidePanel());
        head.addView(close,new LinearLayout.LayoutParams(dp(44),dp(40)));
        panel.addView(head);

        panel.addView(text("Kéo nút WLZ để đổi vị trí",10,MUTED,false));

        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for(int i=0;i<N.length;i++){
            final int id=i;
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(7),0,0,0);
            row.setBackground(round(PANEL2,GRAPHITE,1,10));

            TextView label=text(N[i],12,TEXT,true);
            row.addView(label,new LinearLayout.LayoutParams(0,dp(44),1));

            Switch sw=new Switch(this);
            sw.setChecked(prefs.getBoolean("m_"+id,false));
            sw.setOnCheckedChangeListener((b,c)->{
                prefs.edit().putBoolean("m_"+id,c).apply();
                try{RuntimeBridge.nativeSetModule(id,c);}catch(Throwable ignored){}
            });
            row.addView(sw,new LinearLayout.LayoutParams(-2,dp(44)));

            LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,dp(47));
            rlp.bottomMargin=dp(4);
            list.addView(row,rlp);
        }

        LinearLayout fix= new LinearLayout(this);
        fix.setOrientation(LinearLayout.VERTICAL);
        fix.setPadding(dp(7),dp(8),dp(7),dp(7));
        fix.setBackground(round(PANEL2,MOSS,1,12));
        fix.addView(text("FIX LAG",13,ORANGE,true));

        int current=prefs.getInt("lag_profile",1);
        TextView profileText=text(fixName(current)+"  •  "+(current>=3?"3":"4")+" màu",10,TEXT,true);
        fix.addView(profileText,top(3));

        LinearLayout levels=new LinearLayout(this);
        String[] names={"1 NHẸ","2 MẠNH","3 SIÊU"};
        for(int i=1;i<=3;i++){
            final int level=i;
            Button b=button(names[i-1]);
            b.setOnClickListener(v->{
                applyFix(level);
                profileText.setText(fixName(level)+"  •  "+(level>=3?"3":"4")+" màu");
            });
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);
            if(i>1)p.leftMargin=dp(5);
            levels.addView(b,p);
        }
        fix.addView(levels,top(5));

        Switch effects=new Switch(this);
        effects.setText("Xóa hiệu ứng nặng");
        effects.setTextColor(TEXT);
        effects.setTextSize(11);
        effects.setChecked(prefs.getBoolean("fix_remove_effects",current>=2));
        effects.setOnCheckedChangeListener((b,c)->{
            prefs.edit().putBoolean("fix_remove_effects",c).apply();
            applyFix(prefs.getInt("lag_profile",1));
        });
        fix.addView(effects);

        fix.addView(text("V1 = 4 màu  •  V2 = 4 màu  •  V3 = 3 màu",9,MUTED,false));
        list.addView(fix,wrap());

        list.addView(text("Chỉnh FIX LAG ngoài màn hình client cũng được.",9,MUTED,false),wrap());

        scroll.addView(list);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(385)));

        Button hide=button("Ẩn overlay");
        hide.setOnClickListener(v->stopSelf());
        LinearLayout.LayoutParams hlp=new LinearLayout.LayoutParams(-1,dp(42));
        hlp.topMargin=dp(7);
        panel.addView(hide,hlp);

        panelLp=overlayLp(292,470,14,290,true);
        panel.setVisibility(View.GONE);
        add(panel,panelLp);
    }

    private void applyFix(int level){
        level=Math.max(1,Math.min(3,level));
        boolean removeEffects=prefs.getBoolean("fix_remove_effects",level>=2);
        int colors=level>=3?3:4;
        prefs.edit().putInt("lag_profile",level)
                .putBoolean("fix_remove_effects",removeEffects)
                .putInt("texture_colors",colors).apply();
        try{RuntimeBridge.nativeSetModule(9,true);}catch(Throwable ignored){}
        try{RuntimeBridge.nativeSetParam(3,level);}catch(Throwable ignored){}
        try{RuntimeBridge.nativeSetParam(4,removeEffects?1:0);}catch(Throwable ignored){}
        try{RuntimeBridge.nativeSetParam(5,colors);}catch(Throwable ignored){}
    }

    private String fixName(int level){
        if(level==1)return "CẤP 1 • FIX NHẸ";
        if(level==2)return "CẤP 2 • FIX MẠNH";
        return "CẤP 3 • FIX SIÊU MẠNH";
    }

    private void togglePanel(){
        if(panel.getVisibility()==View.VISIBLE){hidePanel();return;}
        panelLp.x=Math.max(8,bubbleLp.x-dp(8));
        panelLp.y=Math.max(8,bubbleLp.y+dp(62));
        panel.setVisibility(View.VISIBLE);
        try{wm.updateViewLayout(panel,panelLp);}catch(Throwable ignored){}
    }

    private void hidePanel(){panel.setVisibility(View.GONE);}

    private WindowManager.LayoutParams overlayLp(int w,int h,int x,int y,boolean focusable){
        int flags=WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                (focusable?WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL:
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        return new WindowManager.LayoutParams(
                dp(w),dp(h),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,PixelFormat.TRANSLUCENT){{
                    gravity=Gravity.TOP|Gravity.START;
                    this.x=dp(x);this.y=dp(y);
                }};
    }

    private void add(View v,WindowManager.LayoutParams lp){
        try{wm.addView(v,lp);}catch(Throwable ignored){}
    }

    private TextView text(String s,float size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private Button button(String s){
        Button b=new Button(this);
        b.setText(s);b.setTextColor(TEXT);b.setTextSize(10);b.setAllCaps(false);
        b.setBackground(round(MOSS_DARK,MOSS,1,10));
        return b;
    }

    private GradientDrawable round(int fill,int stroke,int width,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(width),stroke);return d;
    }

    private GradientDrawable circle(int fill,int width){
        GradientDrawable d=new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);d.setColor(fill);d.setStroke(dp(width),Color.WHITE);return d;
    }

    private LinearLayout.LayoutParams wrap(){return new LinearLayout.LayoutParams(-1,dp(70));}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=wrap();p.topMargin=dp(m);return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}

    @Override public void onDestroy(){
        try{if(bubble!=null)wm.removeView(bubble);}catch(Throwable ignored){}
        try{if(panel!=null)wm.removeView(panel);}catch(Throwable ignored){}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){return null;}
}
