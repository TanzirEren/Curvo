package com.tanzirdev.curvo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.ArrayList;

/**
 * Work area: the canvas square sits in the middle of a bigger workspace. You can place points
 * outside the canvas; only the part inside the canvas square is exported.
 * One finger on empty space pans, two fingers pinch-zoom.
 */
public class CurveView extends View {
    public interface Listener { void onBeforeChange(); void onChanged(); }

    public ArrayList<Layer> layers = new ArrayList<>();
    public int active = 0;
    public boolean vectorMode = true;
    public int bgColor = 0;      // 0 = transparent
    public int gridMode = 0;     // 0 off, 1 grid, 2 grid + snap
    public Listener listener;

    private final float dp;
    private float zoom = 1f, panX = 0f, panY = 0f;
    private float cx, cy, cs;
    private final Matrix m = new Matrix();
    private final Paint chk = new Paint();
    private final Paint hFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path veilPath = new Path();
    private final RectF rect = new RectF();
    private int dragIdx = -1;
    private boolean moved, panning;
    private float downX, downY, lastX, lastY, lastFx, lastFy;
    private final GestureDetector gd;
    private final ScaleGestureDetector sg;

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
        border.setColor(0xCC0F9D75);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f * dp);
        border.setPathEffect(new DashPathEffect(new float[]{10 * dp, 6 * dp}, 0));
        veil.setColor(0xC2EAF7F0);
        veil.setStyle(Paint.Style.FILL);
        gridP.setColor(0x330F9D75);
        gridP.setStyle(Paint.Style.STROKE);
        gridP.setStrokeWidth(1f * dp);
        chk.setColor(0xFFEEF3F0);

        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (vectorMode && hit(e.getX(), e.getY()) < 0) addPoint(e.getX(), e.getY());
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (!vectorMode || moved) return;
                int i = hit(downX, downY);
                if (i >= 0) deletePoint(i);
            }
        });

        sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) {
                lastFx = d.getFocusX(); lastFy = d.getFocusY();
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector d) {
                float nz = Math.max(0.4f, Math.min(4f, zoom * d.getScaleFactor()));
                float f = nz / zoom;
                float fx = d.getFocusX(), fy = d.getFocusY();
                panX = fx - (fx - panX) * f + (fx - lastFx);
                panY = fy - (fy - panY) * f + (fy - lastFy);
                zoom = nz;
                lastFx = fx; lastFy = fy;
                invalidate();
                return true;
            }
        });
    }

    public void fitView() {
        zoom = 1f; panX = 0f; panY = 0f;
        invalidate();
    }

    private void calc() {
        float w = getWidth();
        cs = w * 0.78f;
        cx = (w - cs) / 2f;
        cy = 84 * dp;
    }

    private float nx(float sx) { return (((sx - panX) / zoom) - cx) / cs; }
    private float ny(float sy) { return (((sy - panY) / zoom) - cy) / cs; }
    private float sxOf(float n) { return panX + zoom * (cx + n * cs); }
    private float syOf(float n) { return panY + zoom * (cy + n * cs); }
    private static float clampN(float v) { return Math.max(-1f, Math.min(2f, v)); }
    private float snap(float v) { return gridMode == 2 ? Math.round(v * 12f) / 12f : v; }

    private Layer act() {
        if (layers.isEmpty()) return null;
        return layers.get(Math.max(0, Math.min(active, layers.size() - 1)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        calc();
        float w = getWidth(), h = getHeight();
        m.reset();
        m.postScale(zoom, zoom);
        m.postTranslate(panX, panY);

        // canvas background (inside the export frame only)
        canvas.save();
        canvas.concat(m);
        clip.reset();
        clip.addRoundRect(cx, cy, cx + cs, cy + cs, 16 * dp, 16 * dp, Path.Direction.CW);
        canvas.clipPath(clip);
        if (bgColor == 0) {
            canvas.drawColor(0xFFFFFFFF);
            float cell = 12 * dp;
            int n = (int) (cs / cell) + 1;
            for (int r = 0; r < n; r++)
                for (int q = 0; q < n; q++)
                    if (((r + q) & 1) == 0)
                        canvas.drawRect(cx + q * cell, cy + r * cell, cx + (q + 1) * cell, cy + (r + 1) * cell, chk);
        } else {
            canvas.drawColor(bgColor);
        }
        if (gridMode > 0) {
            for (int i = 1; i < 12; i++) {
                float t = cs * i / 12f;
                canvas.drawLine(cx + t, cy, cx + t, cy + cs, gridP);
                canvas.drawLine(cx, cy + t, cx + cs, cy + t, gridP);
            }
        }
        canvas.restore();

        // layers (drawn everywhere, also outside the canvas)
        for (Layer l : layers) Renderer.draw(canvas, l, cs, cx, cy, m, (int) w, (int) h);

        // fade everything outside the canvas so it is clear what will be exported
        rect.set(sxOf(0f), syOf(0f), sxOf(1f), syOf(1f));
        veilPath.reset();
        veilPath.setFillType(Path.FillType.EVEN_ODD);
        veilPath.addRect(0, 0, w, h, Path.Direction.CW);
        veilPath.addRoundRect(rect, 16 * dp * zoom, 16 * dp * zoom, Path.Direction.CW);
        canvas.drawPath(veilPath, veil);
        canvas.drawRoundRect(rect, 16 * dp * zoom, 16 * dp * zoom, border);

        Layer a = act();
        if (vectorMode && a != null && a.visible) {
            for (int i = 0; i < a.pts.size() - 1; i++) {
                float[] p = a.pts.get(i), q = a.pts.get(i + 1);
                canvas.drawLine(sxOf(p[0]), syOf(p[1]), sxOf(q[0]), syOf(q[1]), guide);
            }
            for (float[] p : a.pts) {
                canvas.drawCircle(sxOf(p[0]), syOf(p[1]), 9 * dp, hFill);
                canvas.drawCircle(sxOf(p[0]), syOf(p[1]), 9 * dp, hStroke);
            }
        }
    }

    private int hit(float x, float y) {
        Layer l = act();
        if (l == null) return -1;
        calc();
        float bd = 30 * dp;
        int bi = -1;
        for (int i = 0; i < l.pts.size(); i++) {
            float d = (float) Math.hypot(sxOf(l.pts.get(i)[0]) - x, syOf(l.pts.get(i)[1]) - y);
            if (d < bd) { bd = d; bi = i; }
        }
        return bi;
    }

    private void addPoint(float x, float y) {
        Layer l = act();
        if (l == null) return;
        calc();
        float px = snap(clampN(nx(x))), py = snap(clampN(ny(y)));
        int n = l.pts.size();
        int ins = n;
        if (n >= 2) {
            float bd = Float.MAX_VALUE, bt = 0;
            int bi = 0;
            for (int i = 0; i < n - 1; i++) {
                float[] p = l.pts.get(i), q = l.pts.get(i + 1);
                float dx = q[0] - p[0], dy = q[1] - p[1];
                float len2 = dx * dx + dy * dy;
                float t = len2 == 0 ? 0 : ((px - p[0]) * dx + (py - p[1]) * dy) / len2;
                float ct = Math.max(0f, Math.min(1f, t));
                float d = (float) Math.hypot(px - (p[0] + dx * ct), py - (p[1] + dy * ct));
                if (d < bd) { bd = d; bi = i; bt = t; }
            }
            ins = bi + 1;
            if (bi == 0 && bt < 0) ins = 0;
            if (bi == n - 2 && bt > 1) ins = n;
        }
        if (listener != null) listener.onBeforeChange();
        l.pts.add(ins, new float[]{px, py});
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
        sg.onTouchEvent(e);
        int act = e.getActionMasked();
        if (sg.isInProgress() || e.getPointerCount() > 1) {
            dragIdx = -1;
            moved = true;
            if (act == MotionEvent.ACTION_POINTER_UP) {
                int idx = e.getActionIndex() == 0 ? 1 : 0;
                lastX = e.getX(idx); lastY = e.getY(idx);
                panning = true;
            }
            return true;
        }
        gd.onTouchEvent(e);
        Layer l = act();
        float x = e.getX(), y = e.getY();
        switch (act) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = x; downY = lastY = y;
                moved = false; panning = false;
                dragIdx = vectorMode ? hit(x, y) : -1;
                if (dragIdx >= 0 && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragIdx >= 0 && l != null && dragIdx < l.pts.size()) {
                    if (!moved) {
                        if (Math.hypot(x - downX, y - downY) < 6 * dp) break;
                        moved = true;
                        if (listener != null) listener.onBeforeChange();
                    }
                    calc();
                    float[] p = l.pts.get(dragIdx);
                    p[0] = snap(clampN(nx(x)));
                    p[1] = snap(clampN(ny(y)));
                    invalidate();
                } else {
                    if (!panning) {
                        if (Math.hypot(x - downX, y - downY) < 8 * dp) break;
                        panning = true;
                        moved = true;
                    }
                    panX += x - lastX;
                    panY += y - lastY;
                    lastX = x; lastY = y;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (moved && dragIdx >= 0 && listener != null) listener.onChanged();
                dragIdx = -1;
                panning = false;
                break;
        }
        return true;
    }
}
