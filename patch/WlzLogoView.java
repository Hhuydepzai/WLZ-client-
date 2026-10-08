package com.wlz.client;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

public final class WlzLogoView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orange = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean compactCircle;

    public WlzLogoView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        bg.setStyle(Paint.Style.FILL);
        bg.setColor(0xFFFFFFFF);

        orange.setStyle(Paint.Style.FILL);
        orange.setColor(0xFFFF7200);

        white.setStyle(Paint.Style.FILL);
        white.setColor(0xFFFFFFFF);

        shadow.setStyle(Paint.Style.FILL);
        shadow.setColor(0x22000000);
        shadow.setShadowLayer(dp(7), 0, dp(3), 0x33000000);

        text.setStyle(Paint.Style.FILL);
        text.setColor(0xFFFF7200);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        text.setTextAlign(Paint.Align.CENTER);

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(4));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(0xFFFF7200);
    }

    public void setCompactCircle(boolean compact) {
        compactCircle = compact;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h);
        float cx = w * 0.5f;
        float cy = h * 0.5f;

        if (compactCircle) {
            float r = size * 0.45f;
            canvas.drawCircle(cx, cy, r, shadow);
            shadow.clearShadowLayer();

            bg.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, cy, r, bg);

            line.setStrokeWidth(dp(Math.max(2, size * 0.035f)));
            canvas.drawCircle(cx, cy, r - dp(2), line);

            text.setTextSize(size * 0.27f);
            Paint.FontMetrics fm = text.getFontMetrics();
            float base = cy - (fm.ascent + fm.descent) * 0.5f;
            canvas.drawText("WLZ", cx, base, text);

            Path slash = new Path();
            slash.moveTo(cx - size * 0.23f, cy + size * 0.21f);
            slash.lineTo(cx + size * 0.23f, cy + size * 0.21f);
            line.setStrokeWidth(dp(Math.max(2, size * 0.028f)));
            canvas.drawPath(slash, line);
            return;
        }

        RectF card = new RectF(dp(4), dp(4), w - dp(4), h - dp(4));
        canvas.drawRoundRect(card, dp(24), dp(24), shadow);
        shadow.clearShadowLayer();

        bg.setColor(ColorCompat.WHITE);
        canvas.drawRoundRect(card, dp(24), dp(24), bg);

        line.setStrokeWidth(dp(2));
        canvas.drawRoundRect(card, dp(24), dp(24), line);

        text.setTextSize(Math.min(w, h) * 0.31f);
        Paint.FontMetrics fm = text.getFontMetrics();
        float base = cy - (fm.ascent + fm.descent) * 0.38f;
        canvas.drawText("WLZ", cx, base, text);

        float y = cy + size * 0.23f;
        line.setStrokeWidth(dp(4));
        Path underline = new Path();
        underline.moveTo(cx - size * 0.25f, y);
        underline.lineTo(cx + size * 0.25f, y);
        canvas.drawPath(underline, line);

        // Small orange crown/chevron above the mark.
        Path crown = new Path();
        crown.moveTo(cx - size * 0.11f, cy - size * 0.29f);
        crown.lineTo(cx - size * 0.04f, cy - size * 0.20f);
        crown.lineTo(cx, cy - size * 0.30f);
        crown.lineTo(cx + size * 0.04f, cy - size * 0.20f);
        crown.lineTo(cx + size * 0.11f, cy - size * 0.29f);
        crown.lineTo(cx + size * 0.07f, cy - size * 0.15f);
        crown.lineTo(cx - size * 0.07f, cy - size * 0.15f);
        crown.close();
        canvas.drawPath(crown, orange);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static final class ColorCompat {
        static final int WHITE = 0xFFFFFFFF;
    }
}
