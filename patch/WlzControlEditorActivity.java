package com.wlz.client;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class WlzControlEditorActivity extends Activity {
    private static final int BG = Color.rgb(8, 9, 12);
    private static final int PANEL = Color.rgb(18, 20, 25);
    private static final int PANEL_2 = Color.rgb(24, 27, 33);
    private static final int STROKE = Color.rgb(48, 52, 61);
    private static final int ORANGE = Color.rgb(255, 112, 0);
    private static final int TEXT = Color.rgb(246, 247, 250);
    private static final int MUTED = Color.rgb(150, 156, 166);

    private FrameLayout root;
    private TextView captureStatus;
    private int captureAction = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        build();
    }

    private void build() {
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("MAP PHÍM", 19, ORANGE, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button back = button("QUAY LẠI");
        back.setOnClickListener(v -> finish());
        head.addView(back, lp(dp(100), dp(40)));
        page.addView(head);

        captureStatus = text("Chọn MAP rồi bấm phím để gán. Chức năng đã bật sẽ có nút trên HUD.", 9, MUTED, false);
        page.addView(captureStatus, top(5));

        page.addView(text("Kéo nút để đổi vị trí. Hotkey tự hoạt động theo module, không cần bật riêng.", 9, MUTED, false), top(8));
        page.addView(editorCanvas(), top(6, dp(300)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < WlzKeyMapper.ACTIONS.length; i++) {
            list.addView(keyRow(i));
        }
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
    }

    private View editorCanvas() {
        FrameLayout canvas = new FrameLayout(this);
        canvas.setBackground(round(PANEL, STROKE, 1, 16));

        String[] labels = {"ZOOM", "LOOK", "UNLOCK", "BRIGHT", "FPS"};
        int[] actions = {0, 1, 3, 4, 8};
        for (int i = 0; i < labels.length; i++) {
            Button b = touchButton(labels[i], actions[i]);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(78), dp(48));
            p.leftMargin = prefs().getInt("x_" + i, dp(14 + (i % 3) * 92));
            p.topMargin = prefs().getInt("y_" + i, dp(16 + (i / 3) * 70));
            canvas.addView(b, p);
            makeDraggable(b, i, canvas);
        }
        return canvas;
    }

    private Button touchButton(final String label, final int action) {
        Button b = button(label);
        b.setBackground(round(ORANGE, ORANGE, 1, 12));
        b.setOnClickListener(v -> {
            if (action >= 0) {
                boolean enabled = WlzModuleManager.isModuleEnabled(this, action);
                WlzModuleManager.setModuleEnabled(this, action, !enabled);
                Toast.makeText(this, label + (enabled ? " OFF" : " ON"), Toast.LENGTH_SHORT).show();
            }
        });
        return b;
    }

    private void makeDraggable(View v, final int id, final FrameLayout canvas) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            int sx, sy;
            boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) view.getLayoutParams();
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dx = e.getRawX(); dy = e.getRawY();
                    sx = p.leftMargin; sy = p.topMargin; moved = false;
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int nx = sx + (int)(e.getRawX() - dx);
                    int ny = sy + (int)(e.getRawY() - dy);
                    nx = Math.max(0, Math.min(nx, canvas.getWidth() - view.getWidth()));
                    ny = Math.max(0, Math.min(ny, canvas.getHeight() - view.getHeight()));
                    p.leftMargin = nx; p.topMargin = ny;
                    view.setLayoutParams(p);
                    moved = true;
                    prefs().edit().putInt("x_" + id, nx).putInt("y_" + id, ny).apply();
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!moved) view.performClick();
                    return true;
                }
                return true;
            }
        });
    }

    private View keyRow(final int action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(7), dp(7));
        row.setBackground(round(PANEL_2, STROKE, 1, 12));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(WlzKeyMapper.ACTIONS[action], 11, TEXT, true), lp(-1, -2));
        final boolean implemented = WlzKeyMapper.isActionImplemented(action);
        copy.addView(text(implemented
                ? "Phím: " + WlzKeyMapper.keyName(WlzKeyMapper.getKey(this, action))
                : "Chưa được port vào runtime", 9, MUTED, false), top(2));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        Button map = button(!implemented ? "CHƯA HỖ TRỢ" : captureAction == action ? "ĐANG CHỜ..." : "MAP");
        map.setEnabled(implemented);
        map.setAlpha(implemented ? 1f : 0.55f);
        map.setOnClickListener(v -> {
            if (!implemented) return;
            captureAction = action;
            captureStatus.setText("Đang chờ phím cho " + WlzKeyMapper.ACTIONS[action] + "…");
            build();
        });
        row.addView(map, lp(dp(100), dp(42)));

        LinearLayout.LayoutParams rp = lp(-1, dp(58));
        rp.bottomMargin = dp(6);
        row.setLayoutParams(rp);
        return row;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && !event.isLongPress()) {
            if (captureAction >= 0) {
                int key = event.getKeyCode();
                WlzKeyMapper.setKey(this, captureAction, key);
                Toast.makeText(this, WlzKeyMapper.ACTIONS[captureAction] + " = " +
                        WlzKeyMapper.keyName(key), Toast.LENGTH_SHORT).show();
                captureAction = -1;
                captureStatus.setText("Đã lưu map phím.");
                build();
                return true;
            }
            if (WlzKeyMapper.trigger(this, event.getKeyCode())) {
                WlzInGameHud.refreshAllVisuals();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(TEXT);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setPadding(dp(5), 0, dp(5), 0);
        return b;
    }

    private TextView text(String v, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(v); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private LinearLayout.LayoutParams top(int m) { return top(m, -2); }
    private LinearLayout.LayoutParams top(int m, int h) { LinearLayout.LayoutParams p = lp(-1, h); p.topMargin = dp(m); return p; }

    private GradientDrawable round(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(width), stroke); return d;
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("wlz_controls", Context.MODE_PRIVATE);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
