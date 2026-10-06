package com.wlz.client;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public final class WlzLogoView extends View {
    private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orange = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mark = new Path();

    public WlzLogoView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        circle.setStyle(Paint.Style.FILL);
        circle.setColor(0xFFFF7000);

        white.setStyle(Paint.Style.STROKE);
        white.setStrokeWidth(dp(4));
        white.setStrokeCap(Paint.Cap.ROUND);
        white.setStrokeJoin(Paint.Join.ROUND);
        white.setColor(0xFFF4F5F7);

        orange.setStyle(Paint.Style.STROKE);
        orange.setStrokeWidth(dp(4));
        orange.setStrokeCap(Paint.Cap.ROUND);
        orange.setStrokeJoin(Paint.Join.ROUND);
        orange.setColor(0xFFFFC07A);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(2);
        RectF oval = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawOval(oval, circle);

        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float s = Math.min(getWidth(), getHeight()) * 0.34f;

        mark.reset();
        mark.moveTo(cx - s, cy - s * 0.72f);
        mark.lineTo(cx - s * 0.52f, cy + s * 0.78f);
        mark.lineTo(cx, cy - s * 0.10f);
        mark.lineTo(cx + s * 0.40f, cy + s * 0.78f);
        mark.lineTo(cx + s, cy - s * 0.72f);
        canvas.drawPath(mark, white);

        Path z = new Path();
        z.moveTo(cx + s * 0.10f, cy - s * 0.80f);
        z.lineTo(cx + s * 0.86f, cy - s * 0.80f);
        z.lineTo(cx + s * 0.15f, cy + s * 0.80f);
        z.lineTo(cx + s * 0.92f, cy + s * 0.80f);
        canvas.drawPath(z, orange);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
