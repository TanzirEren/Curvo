package com.tanzirdev.curvo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Draws a small rounded rectangle with the proportions of a canvas ratio. */
public class RatioView extends View {
    private final float rw, rh, dp;
    private boolean sel;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public RatioView(Context c, float rw, float rh) {
        super(c);
        this.rw = rw; this.rh = rh;
        dp = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * dp);
    }

    public void setSel(boolean s) { sel = s; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float side = Math.min(w, h) * 0.9f;
        float bw, bh;
        if (rw >= rh) { bw = side; bh = side * rh / rw; } else { bh = side; bw = side * rw / rh; }
        r.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
        fill.setColor(sel ? 0x7034D399 : 0x3334D399);
        stroke.setColor(sel ? 0xFF0F9D75 : 0xAA0F9D75);
        canvas.drawRoundRect(r, 5 * dp, 5 * dp, fill);
        canvas.drawRoundRect(r, 5 * dp, 5 * dp, stroke);
    }
}
