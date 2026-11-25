package com.tanzirdev.curvo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

public class CurveView extends View {
    public interface Listener { void onBeforeChange(); void onChanged(); }

    public ArrayList<Layer> layers = new ArrayList<>();
    public int active = 0;
    public boolean vectorMode = true;
    public Listener listener;

    private final float dp;
    private final Paint chk = new Paint();
    private final Paint hFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private int dragIdx = -1;
    private boolean moved;
    private float downX, downY;
    private final GestureDetector gd;

    public CurveView(Context c) { this(c, null); }

    public CurveView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        hFill.setColor(0xFFFFFFFF);
        hStroke.setColor(0xFF0F9D75);
        hStroke.setStyle(Paint.Style.STROKE);
        hStroke.setStrokeWidth(2.5f * dp);
        guide.setColor(0x660F9D75);
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(1.5f * dp);
        border.setColor(0xFFFFFFFF);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f * dp);
        chk.setColor(0xFFEEF3F0);
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (vectorMode && hit(e.getX(), e.getY()) < 0) addPoint(e.getX(), e.getY());
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                int i = hit(downX, downY);
                if (i >= 0 && !moved) deletePoint(i);
            }
        });
    }

    @Override
    protected void onMeasure(int wm, int hm) {
        int w = MeasureSpec.getSize(wm);
        setMeasuredDimension(w, w);
    }

    private Layer act() {
        if (layers.isEmpty()) return null;
        return layers.get(Math.max(0, Math.min(active, layers.size() - 1)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = getWidth();
        canvas.save();
        clip.reset();
        clip.addRoundRect(0, 0, s, s, 24 * dp, 24 * dp, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawColor(0xFFFFFFFF);
        float cell = 14 * dp;
        for (int r = 0; r * cell < s; r++)
            for (int q = 0; q * cell < s; q++)
                if (((r + q) & 1) == 0) canvas.drawRect(q * cell, r * cell, (q + 1) * cell, (r + 1) * cell, chk);
        for (Layer l : layers) Renderer.draw(canvas, l, s);
        canvas.restore();
        canvas.drawRoundRect(0, 0, s, s, 24 * dp, 24 * dp, border);

        Layer a = act();
        if (vectorMode && a != null && a.visible) {
            for (int i = 0; i < a.pts.size() - 1; i++) {
                float[] p = a.pts.get(i), q = a.pts.get(i + 1);
                canvas.drawLine(p[0] * s, p[1] * s, q[0] * s, q[1] * s, guide);
            }
            for (float[] p : a.pts) {
                canvas.drawCircle(p[0] * s, p[1] * s, 9 * dp, hFill);
                canvas.drawCircle(p[0] * s, p[1] * s, 9 * dp, hStroke);
            }
        }
    }

    private int hit(float x, float y) {
        Layer l = act();
        if (l == null) return -1;
        float s = getWidth();
        float bd = 34 * dp;
        int bi = -1;
        for (int i = 0; i < l.pts.size(); i++) {
            float d = (float) Math.hypot(l.pts.get(i)[0] * s - x, l.pts.get(i)[1] * s - y);
            if (d < bd) { bd = d; bi = i; }
        }
        return bi;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private void addPoint(float x, float y) {
        Layer l = act();
        if (l == null) return;
        float s = getWidth();
        float nx = clamp(x / s), ny = clamp(y / s);
        int n = l.pts.size();
        int ins = n;
        if (n >= 2) {
            float bd = Float.MAX_VALUE, bt = 0;
            int bi = 0;
            for (int i = 0; i < n - 1; i++) {
                float[] p = l.pts.get(i), q = l.pts.get(i + 1);
                float dx = q[0] - p[0], dy = q[1] - p[1];
                float len2 = dx * dx + dy * dy;
                float t = len2 == 0 ? 0 : ((nx - p[0]) * dx + (ny - p[1]) * dy) / len2;
                float ct = Math.max(0f, Math.min(1f, t));
                float d = (float) Math.hypot(nx - (p[0] + dx * ct), ny - (p[1] + dy * ct));
                if (d < bd) { bd = d; bi = i; bt = t; }
            }
            ins = bi + 1;
            if (bi == 0 && bt < 0) ins = 0;
            if (bi == n - 2 && bt > 1) ins = n;
        }
        if (listener != null) listener.onBeforeChange();
        l.pts.add(ins, new float[]{nx, ny});
        if (listener != null) listener.onChanged();
        invalidate();
    }

    private void deletePoint(int i) {
        Layer l = act();
        if (l == null || l.pts.size() <= 2) return;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (listener != null) listener.onBeforeChange();
        l.pts.remove(i);
        if (listener != null) listener.onChanged();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        Layer l = act();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                moved = false;
                dragIdx = hit(downX, downY);
                if (dragIdx >= 0 && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragIdx >= 0 && l != null && dragIdx < l.pts.size()) {
                    if (!moved) {
                        if (Math.hypot(e.getX() - downX, e.getY() - downY) < 6 * dp) break;
                        moved = true;
                        if (listener != null) listener.onBeforeChange();
                    }
                    float s = getWidth();
                    float[] p = l.pts.get(dragIdx);
                    p[0] = clamp(e.getX() / s);
                    p[1] = clamp(e.getY() / s);
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (moved && listener != null) listener.onChanged();
                dragIdx = -1;
                break;
        }
        gd.onTouchEvent(e);
        return true;
    }
}
