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
 * Work area: the canvas rectangle sits in the middle of a bigger workspace. Points can go outside the
 * canvas; only the part inside is exported. One finger on empty space pans, two fingers zoom.
 * Tap a point to select it ("Delete" bubble). In draw mode one finger draws a freehand stroke.
 */
public class CurveView extends View {
    public interface Listener {
        void onBeforeChange();
        void onChanged();
        void onMessage(String m);
        void onStroke(ArrayList<float[]> pts);
        void onSelect(int idx);
    }

    public ArrayList<Layer> layers = new ArrayList<>();
    public int active = 0;
    public boolean vectorMode = true;
    public boolean drawMode = false;
    public int bgColor = 0;      // 0 = transparent
    public int gridMode = 0;     // 0 off, 1 grid, 2 grid + snap
    public int canvasW = 2048, canvasH = 2048;
    public Listener listener;

    private final float dp;
    private float zoom = 1f, panX = 0f, panY = 0f;
    private float cx, cy, u = 1f, cwW, chW;
    private final Matrix m = new Matrix();
    private final Paint chk = new Paint();
    private final Paint hFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint xP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path veilPath = new Path();
    private final Path strokePath = new Path();
    private final RectF rect = new RectF();
    private final RectF bubble = new RectF();
    private final ArrayList<float[]> stroke = new ArrayList<>();
    private boolean strokeOn;
    private boolean bubbleOn;
    private int selIdx = -1;
    private Layer selLayer;
    private boolean wasSelected;
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
        selFill.setColor(0xFF0F9D75);
        selStroke.setColor(0xFFFFFFFF);
        selStroke.setStyle(Paint.Style.STROKE);
        selStroke.setStrokeWidth(3f * dp);
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
        bubbleP.setColor(0xF7FFFFFF);
        bubbleStroke.setStyle(Paint.Style.STROKE);
        bubbleStroke.setStrokeWidth(1.5f * dp);
        xP.setStyle(Paint.Style.STROKE);
        xP.setStrokeWidth(2.6f * dp);
        xP.setStrokeCap(Paint.Cap.ROUND);
        textP.setTextSize(13 * dp);
        textP.setFakeBoldText(true);
        strokeP.setColor(0xFF0F9D75);
        strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(3f * dp);
        strokeP.setStrokeCap(Paint.Cap.ROUND);
        strokeP.setStrokeJoin(Paint.Join.ROUND);

        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                validateSel();
                float x = e.getX(), y = e.getY();
                if (bubbleOn && bubble.contains(x, y)) { deleteSelected(); return true; }
                if (!vectorMode) return true;
                Layer a = act();
                if (a != null && a.locked) { msg("Layer is locked"); return true; }
                int hh = hit(x, y);
                if (hh >= 0) {                    // tapping the selected point again deselects it
                    if (wasSelected) setSel(-1);
                    invalidate();
                    return true;
                }
                addPoint(x, y);
                return true;
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

    private void msg(String s) { if (listener != null) listener.onMessage(s); }

    // ------------------------------------------------------------ canvas size
    public float aw() { return canvasW >= canvasH ? (float) canvasW / canvasH : 1f; }
    public float ah() { return canvasH >= canvasW ? (float) canvasH / canvasW : 1f; }

    /** Changes the canvas size and keeps all drawings centred on the canvas. */
    public void setCanvasSize(int w, int h) {
        float oaw = aw(), oah = ah();
        canvasW = w; canvasH = h;
        float dx = (aw() - oaw) / 2f, dy = (ah() - oah) / 2f;
        for (Layer l : layers) for (float[] p : l.pts) { p[0] += dx; p[1] += dy; }
        fitView();
    }

    public void fitView() {
        zoom = 1f; panX = 0f; panY = 0f;
        invalidate();
    }

    public int getSelectedIndex() {
        validateSel();
        return selIdx;
    }

    private void setSel(int i) {
        selIdx = i;
        selLayer = act();
        if (listener != null) listener.onSelect(i);
    }

    private void calc() {
        float w = getWidth(), h = getHeight();
        float A = aw(), B = ah();
        float boxW = w * 0.80f;
        float boxH = Math.max(w * 0.8f, Math.min(h - 204 * dp, w * 1.1f));
        u = Math.min(boxW / A, boxH / B);
        cwW = A * u;
        chW = B * u;
        cx = (w - cwW) / 2f;
        float slot = Math.max(chW, w * 0.8f);
        cy = 84 * dp + (slot - chW) / 2f;
    }

    private float nx(float sx) { return (((sx - panX) / zoom) - cx) / u; }
    private float ny(float sy) { return (((sy - panY) / zoom) - cy) / u; }
    private float sxOf(float n) { return panX + zoom * (cx + n * u); }
    private float syOf(float n) { return panY + zoom * (cy + n * u); }
    private float clampX(float v) { return Math.max(-1f, Math.min(aw() + 1f, v)); }
    private float clampY(float v) { return Math.max(-1f, Math.min(ah() + 1f, v)); }
    private float snap(float v) { return gridMode == 2 ? Math.round(v * 12f) / 12f : v; }

    private Layer act() {
        if (layers.isEmpty()) return null;
        return layers.get(Math.max(0, Math.min(active, layers.size() - 1)));
    }

    private void validateSel() {
        Layer a = act();
        if (a != selLayer) { selLayer = a; selIdx = -1; }
        if (a == null || selIdx >= a.pts.size()) selIdx = -1;
    }

    // ------------------------------------------------------------ drawing
    @Override
    protected void onDraw(Canvas canvas) {
        calc();
        validateSel();
        float w = getWidth(), h = getHeight();
        float A = aw(), B = ah();
        m.reset();
        m.postScale(zoom, zoom);
        m.postTranslate(panX, panY);

        canvas.save();
        canvas.concat(m);
        clip.reset();
        clip.addRoundRect(cx, cy, cx + cwW, cy + chW, 16 * dp, 16 * dp, Path.Direction.CW);
        canvas.clipPath(clip);
        if (bgColor == 0) {
            canvas.drawColor(0xFFFFFFFF);
            float cell = 12 * dp;
            int cols = (int) (cwW / cell) + 1, rowsN = (int) (chW / cell) + 1;
            for (int r = 0; r < rowsN; r++)
                for (int q = 0; q < cols; q++)
                    if (((r + q) & 1) == 0)
                        canvas.drawRect(cx + q * cell, cy + r * cell, cx + (q + 1) * cell, cy + (r + 1) * cell, chk);
        } else {
            canvas.drawColor(bgColor);
        }
        if (gridMode > 0) {
            int gx = (int) Math.floor(A * 12f - 0.001f), gy = (int) Math.floor(B * 12f - 0.001f);
            for (int i = 1; i <= gx; i++) {
                float t = u * i / 12f;
                canvas.drawLine(cx + t, cy, cx + t, cy + chW, gridP);
            }
            for (int i = 1; i <= gy; i++) {
                float t = u * i / 12f;
                canvas.drawLine(cx, cy + t, cx + cwW, cy + t, gridP);
            }
        }
        canvas.restore();

        for (Layer l : layers) Renderer.draw(canvas, l, u, A, B, cx, cy, m, (int) w, (int) h);

        rect.set(sxOf(0f), syOf(0f), sxOf(A), syOf(B));
        veilPath.reset();
        veilPath.setFillType(Path.FillType.EVEN_ODD);
        veilPath.addRect(0, 0, w, h, Path.Direction.CW);
        veilPath.addRoundRect(rect, 16 * dp * zoom, 16 * dp * zoom, Path.Direction.CW);
        canvas.drawPath(veilPath, veil);
        canvas.drawRoundRect(rect, 16 * dp * zoom, 16 * dp * zoom, border);

        Layer a = act();
        bubbleOn = false;
        bubble.setEmpty();
        if (vectorMode && !drawMode && a != null && a.visible && !a.locked) {
            for (int i = 0; i < a.pts.size() - 1; i++) {
                float[] p = a.pts.get(i), q = a.pts.get(i + 1);
                canvas.drawLine(sxOf(p[0]), syOf(p[1]), sxOf(q[0]), syOf(q[1]), guide);
            }
            for (int i = 0; i < a.pts.size(); i++) {
                if (i == selIdx) continue;
                float[] p = a.pts.get(i);
                canvas.drawCircle(sxOf(p[0]), syOf(p[1]), 9 * dp, hFill);
                canvas.drawCircle(sxOf(p[0]), syOf(p[1]), 9 * dp, hStroke);
            }
            if (selIdx >= 0) {
                float[] p = a.pts.get(selIdx);
                float px = sxOf(p[0]), py = syOf(p[1]);
                canvas.drawCircle(px, py, 12 * dp, selFill);
                canvas.drawCircle(px, py, 12 * dp, selStroke);
                if (!(dragIdx >= 0 && moved)) drawBubble(canvas, a, px, py, w);
            }
        }
        if (strokeOn && stroke.size() > 1) {
            strokePath.reset();
            strokePath.moveTo(stroke.get(0)[0], stroke.get(0)[1]);
            for (int i = 1; i < stroke.size(); i++) strokePath.lineTo(stroke.get(i)[0], stroke.get(i)[1]);
            canvas.drawPath(strokePath, strokeP);
        }
    }

    private void drawBubble(Canvas canvas, Layer a, float px, float py, float w) {
        float bw = 98 * dp, bh = 38 * dp;
        float top = py - 18 * dp - bh;
        if (top < 76 * dp) top = py + 18 * dp;
        float left = Math.max(8 * dp, Math.min(w - 8 * dp - bw, px - bw / 2f));
        bubble.set(left, top, left + bw, top + bh);
        bubbleOn = true;
        boolean can = a.pts.size() > 2;
        int col = can ? 0xFFE5484D : 0xFF9AA8A2;
        bubbleStroke.setColor(can ? 0x66E5484D : 0x449AA8A2);
        canvas.drawRoundRect(bubble, bh / 2f, bh / 2f, bubbleP);
        canvas.drawRoundRect(bubble, bh / 2f, bh / 2f, bubbleStroke);
        float ix = left + 22 * dp, iy = top + bh / 2f, r = 5 * dp;
        xP.setColor(col);
        canvas.drawLine(ix - r, iy - r, ix + r, iy + r, xP);
        canvas.drawLine(ix - r, iy + r, ix + r, iy - r, xP);
        textP.setColor(col);
        canvas.drawText("Delete", left + 38 * dp, iy - (textP.ascent() + textP.descent()) / 2f, textP);
    }

    // ------------------------------------------------------------ points
    private int hit(float x, float y) {
        Layer l = act();
        if (l == null || l.locked) return -1;
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
        float px = snap(clampX(nx(x))), py = snap(clampY(ny(y)));
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
        l.pts.add(ins, new float[]{px, py, 1f});
        setSel(ins);                 // the new point is selected, so a wrong tap can be deleted right away
        if (listener != null) listener.onChanged();
        invalidate();
    }

    private void deleteSelected() {
        Layer l = act();
        if (l == null || selIdx < 0 || selIdx >= l.pts.size()) return;
        if (l.pts.size() <= 2) { msg("A line needs at least 2 points"); return; }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (listener != null) listener.onBeforeChange();
        l.pts.remove(selIdx);
        setSel(-1);
        if (listener != null) listener.onChanged();
        invalidate();
    }

    // ------------------------------------------------------------ freehand
    private boolean handleDraw(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stroke.clear();
                strokeOn = true;
                stroke.add(new float[]{x, y, e.getPressure()});
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!strokeOn) break;
                float[] last = stroke.get(stroke.size() - 1);
                if (Math.hypot(x - last[0], y - last[1]) > 3 * dp) {
                    stroke.add(new float[]{x, y, e.getPressure()});
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (strokeOn) {
                    stroke.add(new float[]{x, y, e.getPressure()});
                    finishStroke();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                strokeOn = false;
                stroke.clear();
                invalidate();
                break;
        }
        return true;
    }

    private void finishStroke() {
        strokeOn = false;
        if (stroke.size() >= 2 && listener != null) {
            calc();
            ArrayList<float[]> pts = new ArrayList<>();
            for (float[] s : stroke) pts.add(new float[]{clampX(nx(s[0])), clampY(ny(s[1])), s[2]});
            stroke.clear();
            listener.onStroke(pts);
        }
        stroke.clear();
        invalidate();
    }

    // ------------------------------------------------------------ touch
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        sg.onTouchEvent(e);
        int action = e.getActionMasked();
        if (sg.isInProgress() || e.getPointerCount() > 1) {
            dragIdx = -1;
            moved = true;
            if (strokeOn) { strokeOn = false; stroke.clear(); invalidate(); }
            if (action == MotionEvent.ACTION_POINTER_UP) {
                int idx = e.getActionIndex() == 0 ? 1 : 0;
                lastX = e.getX(idx); lastY = e.getY(idx);
                panning = true;
            }
            return true;
        }
        if (drawMode) return handleDraw(e);
        validateSel();
        gd.onTouchEvent(e);
        Layer l = act();
        float x = e.getX(), y = e.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                downX = lastX = x; downY = lastY = y;
                moved = false; panning = false; wasSelected = false;
                if (bubbleOn && bubble.contains(x, y)) {
                    dragIdx = -1;
                } else {
                    int hh = vectorMode ? hit(x, y) : -1;
                    wasSelected = hh >= 0 && hh == selIdx;
                    dragIdx = hh;
                    if (hh >= 0) { setSel(hh); invalidate(); }
                }
                if (dragIdx >= 0 && getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragIdx >= 0 && l != null && dragIdx < l.pts.size()) {
                    if (!moved) {
                        if (Math.hypot(x - downX, y - downY) < 6 * dp) break;
                        moved = true;
                        if (listener != null) listener.onBeforeChange();
                    }
                    calc();
                    float[] p = l.pts.get(dragIdx);
                    p[0] = snap(clampX(nx(x)));
                    p[1] = snap(clampY(ny(y)));
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
                invalidate();
                break;
        }
        return true;
    }
}
