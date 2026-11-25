package com.tanzirdev.curvo;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.List;

/** Draws ribbons as filled polygons, so the start and end are always flat (plain) unless "round" is on. */
public class Renderer {

    public static Path curvePath(Layer l, float size) {
        Path p = new Path();
        int n = l.pts.size();
        if (n < 2) return p;
        float k = l.smooth / 3f;
        p.moveTo(l.pts.get(0)[0] * size, l.pts.get(0)[1] * size);
        for (int i = 0; i < n - 1; i++) {
            float[] p0 = l.pts.get(Math.max(i - 1, 0));
            float[] p1 = l.pts.get(i);
            float[] p2 = l.pts.get(i + 1);
            float[] p3 = l.pts.get(Math.min(i + 2, n - 1));
            p.cubicTo((p1[0] + (p2[0] - p0[0]) * k) * size, (p1[1] + (p2[1] - p0[1]) * k) * size,
                      (p2[0] - (p3[0] - p1[0]) * k) * size, (p2[1] - (p3[1] - p1[1]) * k) * size,
                      p2[0] * size, p2[1] * size);
        }
        return p;
    }

    private static void seg(Canvas c, PathMeasure pm, float len, float a, float b, Layer l,
                            float w, float extra, Paint paint) {
        float s0 = a * len, s1 = b * len;
        int steps = Math.max(4, (int) ((s1 - s0) / 4f));
        float[] pos = new float[2], tan = new float[2];
        float[] lx = new float[steps + 1], ly = new float[steps + 1];
        float[] rx = new float[steps + 1], ry = new float[steps + 1];
        float[] ex = new float[6];
        for (int k = 0; k <= steps; k++) {
            float s = s0 + (s1 - s0) * k / steps;
            pm.getPosTan(s, pos, tan);
            float f = s / len;
            float tf = Math.max(0.03f, 1f - l.taper * Math.abs(2f * f - 1f));
            float h = Math.max(0.5f, (w * tf + extra) / 2f);
            float nx = -tan[1], ny = tan[0];
            lx[k] = pos[0] + nx * h; ly[k] = pos[1] + ny * h;
            rx[k] = pos[0] - nx * h; ry[k] = pos[1] - ny * h;
            if (k == 0) { ex[0] = pos[0]; ex[1] = pos[1]; ex[2] = h; }
            if (k == steps) { ex[3] = pos[0]; ex[4] = pos[1]; ex[5] = h; }
        }
        Path poly = new Path();
        poly.moveTo(lx[0], ly[0]);
        for (int k = 1; k <= steps; k++) poly.lineTo(lx[k], ly[k]);
        for (int k = steps; k >= 0; k--) poly.lineTo(rx[k], ry[k]);
        poly.close();
        c.drawPath(poly, paint);
        if (l.round) {
            c.drawCircle(ex[0], ex[1], ex[2], paint);
            c.drawCircle(ex[3], ex[4], ex[5], paint);
        }
    }

    private static void drawShape(Canvas c, Layer l, Path cp, float size, Paint paint, float extra) {
        PathMeasure pm = new PathMeasure(cp, false);
        float len = pm.getLength();
        if (len < 1f) return;
        float w = l.width * size;
        if (l.dash <= 0.001f) {
            seg(c, pm, len, 0f, 1f, l, w, extra, paint);
        } else {
            float d = len * (0.01f + l.dash * 0.12f);
            float g = d * 0.7f;
            for (float s = 0; s < len; s += d + g) {
                seg(c, pm, len, s / len, Math.min(len, s + d) / len, l, w, extra, paint);
            }
        }
    }

    public static void draw(Canvas c, Layer l, float size) {
        if (!l.visible || l.pts.size() < 2) return;
        Path cp = curvePath(l, size);
        int a = Math.round(Math.max(0f, Math.min(1f, l.opacity)) * 255);

        if (l.glow > 0.01f) {
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setColor(l.c1);
            g.setAlpha(Math.round(a * 0.9f));
            g.setMaskFilter(new BlurMaskFilter(Math.max(1f, l.glow * size * 0.05f), BlurMaskFilter.Blur.NORMAL));
            drawShape(c, l, cp, size, g, l.glow * size * 0.01f);
        }
        if (l.outline > 0.01f) {
            Paint o = new Paint(Paint.ANTI_ALIAS_FLAG);
            o.setColor(l.outlineColor);
            o.setAlpha(a);
            drawShape(c, l, cp, size, o, l.outline * size * 0.03f);
        }

        if (l.mask != null) {
            int s = Math.max(1, Math.round(size));
            Bitmap off = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas oc = new Canvas(off);
            Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
            m.setColor(Color.BLACK);
            drawShape(oc, l, cp, size, m, 0f);
            Paint x = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            x.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            int bw = l.mask.getWidth(), bh = l.mask.getHeight(), side = Math.min(bw, bh);
            Rect src = new Rect((bw - side) / 2, (bh - side) / 2, (bw + side) / 2, (bh + side) / 2);
            oc.drawBitmap(l.mask, src, new RectF(0, 0, s, s), x);
            Paint op = new Paint(Paint.FILTER_BITMAP_FLAG);
            op.setAlpha(a);
            c.drawBitmap(off, 0, 0, op);
            off.recycle();
        } else {
            float[] f0 = l.pts.get(0), f1 = l.pts.get(l.pts.size() - 1);
            float x1 = f1[0] * size, y1 = f1[1] * size;
            if (Math.abs(x1 - f0[0] * size) + Math.abs(y1 - f0[1] * size) < 1f) x1 += 1f;
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(new LinearGradient(f0[0] * size, f0[1] * size, x1, y1, l.c1, l.c2, Shader.TileMode.CLAMP));
            f.setAlpha(a);
            drawShape(c, l, cp, size, f, 0f);
        }
    }

    public static Bitmap export(List<Layer> layers, int px, int bg) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (bg != 0) c.drawColor(bg);
        for (Layer l : layers) draw(c, l, px);
        return b;
    }
}
