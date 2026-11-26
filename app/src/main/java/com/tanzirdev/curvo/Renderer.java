package com.tanzirdev.curvo;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds ribbon geometry (polygons) once, then draws it to a Canvas or writes it to SVG.
 * Supports variable width, taper, dash, round caps, gradients, masks, patterns, symmetry and blend modes.
 */
public class Renderer {

    public static class Shape {
        public final ArrayList<float[]> polys = new ArrayList<>();   // closed polygons: x0,y0,x1,y1,...
        public final ArrayList<float[]> circs = new ArrayList<>();   // cx,cy,r
    }

    public static class Geo {
        public Shape shadow, glow, outline;
        public final Shape body = new Shape(), detail = new Shape();
        public final ArrayList<float[]> pat = new ArrayList<>();    // {0,x1,y1,x2,y2,thick} line | {1,cx,cy,r,alpha} dot
        public float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
        public boolean valid;
        void upd(float x, float y) {
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
    }

    // ------------------------------------------------------------ curve + widths
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

    private static float pw(Layer l, int i) {
        float[] p = l.pts.get(i);
        return p.length > 2 ? p[2] : 1f;
    }

    private static float[] knots(Layer l) {
        int n = l.pts.size();
        float[] k = new float[n];
        float[] d = new float[n];
        float tot = 0f;
        for (int i = 1; i < n; i++) {
            d[i] = (float) Math.hypot(l.pts.get(i)[0] - l.pts.get(i - 1)[0], l.pts.get(i)[1] - l.pts.get(i - 1)[1]);
            tot += d[i];
        }
        float acc = 0f;
        for (int i = 1; i < n; i++) {
            acc += d[i];
            k[i] = tot > 0f ? acc / tot : i / (float) (n - 1);
        }
        return k;
    }

    private static float wmAt(Layer l, float f, float[] k) {
        int n = l.pts.size();
        if (f <= k[0]) return pw(l, 0);
        for (int i = 0; i < n - 1; i++) {
            if (f <= k[i + 1]) {
                float t = (f - k[i]) / Math.max(1e-6f, k[i + 1] - k[i]);
                t = t * t * (3f - 2f * t);
                return pw(l, i) + (pw(l, i + 1) - pw(l, i)) * t;
            }
        }
        return pw(l, n - 1);
    }

    // ------------------------------------------------------------ gradient
    /** {0 linear|1 radial, a, b, c, d}: linear = x1,y1,x2,y2; radial = cx,cy,r,0. */
    public static float[] gradientSpec(Layer l, float size) {
        float minx = 1e9f, miny = 1e9f, maxx = -1e9f, maxy = -1e9f;
        for (float[] p : l.pts) {
            minx = Math.min(minx, p[0] * size); maxx = Math.max(maxx, p[0] * size);
            miny = Math.min(miny, p[1] * size); maxy = Math.max(maxy, p[1] * size);
        }
        float[] r = new float[5];
        if (l.gradMode == 2) {
            r[0] = 1f;
            r[1] = (minx + maxx) / 2f;
            r[2] = (miny + maxy) / 2f;
            r[3] = Math.max(Math.max(maxx - minx, maxy - miny) / 2f, 1f);
        } else if (l.gradMode == 1) {
            double a = Math.toRadians(l.gradAngle);
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            float cx = (minx + maxx) / 2f, cy = (miny + maxy) / 2f;
            float half = (Math.abs(dx) * (maxx - minx) + Math.abs(dy) * (maxy - miny)) / 2f + 1f;
            r[1] = cx - dx * half; r[2] = cy - dy * half;
            r[3] = cx + dx * half; r[4] = cy + dy * half;
        } else {
            float[] f0 = l.pts.get(0), f1 = l.pts.get(l.pts.size() - 1);
            r[1] = f0[0] * size; r[2] = f0[1] * size;
            r[3] = f1[0] * size; r[4] = f1[1] * size;
            if (Math.abs(r[3] - r[1]) + Math.abs(r[4] - r[2]) < 1f) r[3] += 1f;
        }
        return r;
    }

    private static Shader makeShader(Layer l, float size) {
        int[] cols = l.colors();
        float[] pos = new float[cols.length];
        for (int i = 0; i < cols.length; i++) pos[i] = i / (float) (cols.length - 1);
        float[] g = gradientSpec(l, size);
        if (g[0] == 1f) return new RadialGradient(g[1], g[2], g[3], cols, pos, Shader.TileMode.CLAMP);
        return new LinearGradient(g[1], g[2], g[3], g[4], cols, pos, Shader.TileMode.CLAMP);
    }

    // ------------------------------------------------------------ geometry building
    private static void seg(Layer l, PathMeasure pm, float len, float s0, float s1, float w, float extra,
                            float[] kn, Shape out, Geo bb) {
        if (s1 - s0 < 0.5f) return;
        int steps = Math.max(4, (int) ((s1 - s0) / 4f));
        float[] pos = new float[2], tan = new float[2];
        float[] poly = new float[(steps + 1) * 4];
        float x0 = 0, y0 = 0, h0 = 0, x1 = 0, y1 = 0, h1 = 0;
        for (int k = 0; k <= steps; k++) {
            float s = s0 + (s1 - s0) * k / steps;
            pm.getPosTan(s, pos, tan);
            float f = s / len;
            float tf = Math.max(0.03f, 1f - l.taper * Math.abs(2f * f - 1f));
            float h = Math.max(0.5f, (w * tf * wmAt(l, f, kn) + extra) / 2f);
            float nx = -tan[1], ny = tan[0];
            float lx = pos[0] + nx * h, ly = pos[1] + ny * h;
            float rx = pos[0] - nx * h, ry = pos[1] - ny * h;
            poly[2 * k] = lx; poly[2 * k + 1] = ly;
            int slot = (steps + 1) + (steps - k);
            poly[2 * slot] = rx; poly[2 * slot + 1] = ry;
            if (k == 0) { x0 = pos[0]; y0 = pos[1]; h0 = h; }
            if (k == steps) { x1 = pos[0]; y1 = pos[1]; h1 = h; }
            if (bb != null) { bb.upd(lx, ly); bb.upd(rx, ry); }
        }
        out.polys.add(poly);
        if (l.round) {
            out.circs.add(new float[]{x0, y0, h0});
            out.circs.add(new float[]{x1, y1, h1});
            if (bb != null) {
                bb.upd(x0 - h0, y0 - h0); bb.upd(x0 + h0, y0 + h0);
                bb.upd(x1 - h1, y1 - h1); bb.upd(x1 + h1, y1 + h1);
            }
        }
    }

    private static void ribbon(Layer l, PathMeasure pm, float len, float size, float extra, float[] kn,
                               Shape out, Geo bb) {
        float w = l.width * size;
        float end = len * Math.max(0.0001f, Math.min(1f, l.reveal));
        if (l.dash <= 0.001f) {
            seg(l, pm, len, 0f, end, w, extra, kn, out, bb);
        } else {
            float d = len * (0.01f + l.dash * 0.12f);
            float g = d * 0.7f;
            for (float s = 0f; s < end; s += d + g) seg(l, pm, len, s, Math.min(end, s + d), w, extra, kn, out, bb);
        }
    }

    private static void offSeg(Layer l, PathMeasure pm, float len, float[] kn, float w, float off, float thick,
                               float s0, float s1, Shape out) {
        if (s1 - s0 < 0.5f) return;
        int steps = Math.max(2, (int) ((s1 - s0) / 4f));
        float[] pos = new float[2], tan = new float[2];
        float[] poly = new float[(steps + 1) * 4];
        for (int k = 0; k <= steps; k++) {
            float s = s0 + (s1 - s0) * k / steps;
            pm.getPosTan(s, pos, tan);
            float f = s / len;
            float tf = Math.max(0.03f, 1f - l.taper * Math.abs(2f * f - 1f));
            float full = w * tf * wmAt(l, f, kn);
            float nx = -tan[1], ny = tan[0];
            float o1 = (off - thick / 2f) * full, o2 = (off + thick / 2f) * full;
            if (Math.abs(o2 - o1) < 0.8f) { float mid = (o1 + o2) / 2f; o1 = mid - 0.4f; o2 = mid + 0.4f; }
            poly[2 * k] = pos[0] + nx * o2; poly[2 * k + 1] = pos[1] + ny * o2;
            int slot = (steps + 1) + (steps - k);
            poly[2 * slot] = pos[0] + nx * o1; poly[2 * slot + 1] = pos[1] + ny * o1;
        }
        out.polys.add(poly);
    }

    private static void offsetRib(Layer l, PathMeasure pm, float len, float[] kn, float w, float off, float thick,
                                  float s0, float s1, float dash, Shape out) {
        if (dash <= 0f) {
            offSeg(l, pm, len, kn, w, off, thick, s0, s1, out);
        } else {
            for (float s = s0; s < s1; s += dash * 1.8f) offSeg(l, pm, len, kn, w, off, thick, s, Math.min(s1, s + dash), out);
        }
    }

    /** Road / rail marks that follow the line. */
    private static void detail(Layer l, PathMeasure pm, float len, float size, float[] kn, Shape out) {
        float w = l.width * size;
        float end = len * Math.max(0.0001f, Math.min(1f, l.reveal));
        if (l.pat == 4) {
            offsetRib(l, pm, len, kn, w, 0.42f, 0.05f, 0f, end, 0f, out);
            offsetRib(l, pm, len, kn, w, -0.42f, 0.05f, 0f, end, 0f, out);
            offsetRib(l, pm, len, kn, w, 0f, 0.07f, 0f, end, Math.max(6f, w * 0.55f), out);
        } else if (l.pat == 5) {
            offsetRib(l, pm, len, kn, w, 0.30f, 0.09f, 0f, end, 0f, out);
            offsetRib(l, pm, len, kn, w, -0.30f, 0.09f, 0f, end, 0f, out);
            float step = Math.max(5f, w * 0.55f);
            float[] pos = new float[2], tan = new float[2];
            for (float s = step / 2f; s < end; s += step) {
                pm.getPosTan(s, pos, tan);
                float f = s / len;
                float tf = Math.max(0.03f, 1f - l.taper * Math.abs(2f * f - 1f));
                float full = w * tf * wmAt(l, f, kn);
                float hl = full * 0.46f, ht = Math.max(1f, full * 0.07f);
                float nx = -tan[1], ny = tan[0], tx = tan[0], ty = tan[1];
                out.polys.add(new float[]{
                    pos[0] + nx * hl + tx * ht, pos[1] + ny * hl + ty * ht,
                    pos[0] + nx * hl - tx * ht, pos[1] + ny * hl - ty * ht,
                    pos[0] - nx * hl - tx * ht, pos[1] - ny * hl - ty * ht,
                    pos[0] - nx * hl + tx * ht, pos[1] - ny * hl + ty * ht});
            }
        }
    }

    /** Stripes, dots, noise: primitives over the layer's bounding box (clipped to the ribbon later). */
    private static void genPattern(Layer l, float size, Geo g) {
        float x0 = g.minX, y0 = g.minY, x1 = g.maxX, y1 = g.maxY;
        float bw = x1 - x0, bh = y1 - y0;
        if (bw <= 0f || bh <= 0f) return;
        float ps = Math.max(0f, Math.min(1f, l.patScale));
        if (l.pat == 1) {
            float sp = size * (0.025f + 0.12f * ps);
            float step = sp * 1.414f;
            if ((bw + bh) / step > 500f) step = (bw + bh) / 500f;
            float thick = sp * 0.4f;
            for (float cc = x0 - y1; cc <= x1 - y0; cc += step)
                g.pat.add(new float[]{0f, cc + y0, y0, cc + y1, y1, thick});
        } else if (l.pat == 2) {
            float sp = size * (0.03f + 0.12f * ps);
            while ((bw / sp) * (bh / sp) > 3500f) sp *= 1.2f;
            float r = sp * 0.26f;
            int row = 0;
            for (float y = y0; y <= y1; y += sp, row++)
                for (float x = x0 + (row % 2 == 0 ? 0f : sp / 2f); x <= x1; x += sp)
                    g.pat.add(new float[]{1f, x, y, r, 1f});
        } else if (l.pat == 3) {
            float sp = size * (0.02f + 0.06f * ps);
            int n = (int) Math.min(3000f, (bw * bh) / (sp * sp) * 2f);
            Random rd = new Random(1234);
            for (int i = 0; i < n; i++)
                g.pat.add(new float[]{1f, x0 + rd.nextFloat() * bw, y0 + rd.nextFloat() * bh,
                        sp * (0.08f + 0.18f * rd.nextFloat()), 0.25f + 0.65f * rd.nextFloat()});
        }
    }

    public static Geo build(Layer l, float size) {
        Geo g = new Geo();
        if (l.pts.size() < 2) return g;
        Path cp = curvePath(l, size);
        PathMeasure pm = new PathMeasure(cp, false);
        float len = pm.getLength();
        if (len < 1f) return g;
        float[] kn = knots(l);
        if (l.shadow > 0.01f) { g.shadow = new Shape(); ribbon(l, pm, len, size, 0f, kn, g.shadow, null); }
        if (l.glow > 0.01f) { g.glow = new Shape(); ribbon(l, pm, len, size, l.glow * size * 0.01f, kn, g.glow, null); }
        if (l.outline > 0.01f) { g.outline = new Shape(); ribbon(l, pm, len, size, l.outline * size * 0.03f, kn, g.outline, null); }
        ribbon(l, pm, len, size, 0f, kn, g.body, g);
        if (g.minX > g.maxX) return g;
        if (l.pat == 4 || l.pat == 5) detail(l, pm, len, size, kn, g.detail);
        if (l.pat >= 1 && l.pat <= 3) genPattern(l, size, g);
        g.valid = true;
        return g;
    }

    // ------------------------------------------------------------ symmetry
    public static ArrayList<Layer> symCopies(Layer l, float aw, float ah) {
        ArrayList<Layer> out = new ArrayList<>();
        out.add(l);
        switch (l.sym) {
            case 1: out.add(flipCopy(l, aw, ah, 1)); break;
            case 2: out.add(flipCopy(l, aw, ah, 2)); break;
            case 3:
                out.add(flipCopy(l, aw, ah, 1));
                out.add(flipCopy(l, aw, ah, 2));
                out.add(flipCopy(l, aw, ah, 3));
                break;
            case 4:
                for (int k = 1; k < 6; k++) out.add(rotCopy(l, aw, ah, k * 60f));
                break;
            default: break;
        }
        return out;
    }

    private static Layer flipCopy(Layer l, float aw, float ah, int mode) {
        Layer c = l.copy();
        c.sym = 0;
        for (float[] p : c.pts) {
            if (mode == 1 || mode == 3) p[0] = aw - p[0];
            if (mode == 2 || mode == 3) p[1] = ah - p[1];
        }
        return c;
    }

    private static Layer rotCopy(Layer l, float aw, float ah, float deg) {
        Layer c = l.copy();
        c.sym = 0;
        double a = Math.toRadians(deg);
        float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
        float cx = aw / 2f, cy = ah / 2f;
        for (float[] p : c.pts) {
            float dx = p[0] - cx, dy = p[1] - cy;
            p[0] = cx + dx * cs - dy * sn;
            p[1] = cy + dx * sn + dy * cs;
        }
        return c;
    }

    // ------------------------------------------------------------ drawing
    private static void drawShape(Canvas c, Shape s, Paint p) {
        if (!s.polys.isEmpty()) {
            Path path = new Path();
            for (float[] q : s.polys) {
                path.moveTo(q[0], q[1]);
                for (int i = 2; i + 1 < q.length; i += 2) path.lineTo(q[i], q[i + 1]);
                path.close();
            }
            c.drawPath(path, p);
        }
        for (float[] q : s.circs) c.drawCircle(q[0], q[1], q[2], p);
    }

    private static PorterDuff.Mode blendMode(int b) {
        switch (b) {
            case 1: return PorterDuff.Mode.MULTIPLY;
            case 2: return PorterDuff.Mode.SCREEN;
            case 3: return PorterDuff.Mode.OVERLAY;
            case 4: return PorterDuff.Mode.DARKEN;
            case 5: return PorterDuff.Mode.LIGHTEN;
            default: return PorterDuff.Mode.SRC_OVER;
        }
    }

    /** Simple version: square canvas of side "size" at (0,0), no transform. */
    public static void draw(Canvas c, Layer l, float size) {
        draw(c, l, size, 1f, 1f, 0f, 0f, null, Math.round(size), Math.round(size));
    }

    /**
     * Full version. "size" = pixels per design unit (canvas short side). The canvas is aw x ah units with its
     * top-left at (ox, oy) in world space. m maps world to screen (null = identity). w/h = surface size in pixels.
     */
    public static void draw(Canvas c, Layer l, float size, float aw, float ah,
                            float ox, float oy, Matrix m, int w, int h) {
        if (!l.visible || l.pts.size() < 2 || l.reveal <= 0.002f) return;
        ArrayList<Layer> copies = symCopies(l, aw, ah);
        int sc = -1;
        if (l.blend != 0) {
            Paint bp = new Paint();
            bp.setXfermode(new PorterDuffXfermode(blendMode(l.blend)));
            sc = c.saveLayer(new RectF(0, 0, w, h), bp);
        }
        for (Layer k : copies) drawOne(c, k, size, aw, ah, ox, oy, m, w, h);
        if (sc >= 0) c.restoreToCount(sc);
    }

    private static void drawDetail(Canvas c, Geo g, Layer l, int a) {
        if (g.detail.polys.isEmpty()) return;
        Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);
        dp.setColor(l.patColor);
        dp.setAlpha(Math.round(Color.alpha(l.patColor) * a / 255f));
        drawShape(c, g.detail, dp);
    }

    private static void drawOne(Canvas c, Layer l, float size, float aw, float ah,
                                float ox, float oy, Matrix m, int w, int h) {
        Geo g = build(l, size);
        if (!g.valid) return;
        int a = Math.round(Math.max(0f, Math.min(1f, l.opacity)) * 255);
        boolean off = l.mask != null || (l.pat >= 1 && l.pat <= 3);

        c.save();
        if (m != null) c.concat(m);
        c.translate(ox, oy);
        if (g.shadow != null) {
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(Color.BLACK);
            sp.setAlpha(Math.round(a * 0.65f * Math.min(1f, l.shadow)));
            sp.setMaskFilter(new BlurMaskFilter(Math.max(1f, (0.004f + l.shBlur * 0.05f) * size), BlurMaskFilter.Blur.NORMAL));
            c.save();
            c.translate(l.shDist * size * 0.03f, l.shDist * size * 0.05f);
            drawShape(c, g.shadow, sp);
            c.restore();
        }
        if (g.glow != null) {
            Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
            gp.setColor(l.c1);
            gp.setAlpha(Math.round(a * 0.9f));
            gp.setMaskFilter(new BlurMaskFilter(Math.max(1f, l.glow * size * 0.05f), BlurMaskFilter.Blur.NORMAL));
            drawShape(c, g.glow, gp);
        }
        if (g.outline != null) {
            Paint op = new Paint(Paint.ANTI_ALIAS_FLAG);
            op.setColor(l.outlineColor);
            op.setAlpha(a);
            drawShape(c, g.outline, op);
        }
        if (!off) {
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setShader(makeShader(l, size));
            f.setAlpha(a);
            drawShape(c, g.body, f);
            drawDetail(c, g, l, a);
            c.restore();
            return;
        }
        c.restore();

        // Offscreen: ribbon filled with image mask and/or pattern, then composited.
        float cwU = aw * size, chU = ah * size;
        RectF wr = new RectF(g.minX - 1f, g.minY - 1f, g.maxX + 1f, g.maxY + 1f);
        if (l.mask != null && !wr.intersect(0f, 0f, cwU, chU)) return;
        RectF r = new RectF(wr);
        r.offset(ox, oy);
        if (m != null) m.mapRect(r);
        int L = Math.max(0, (int) Math.floor(r.left)), T = Math.max(0, (int) Math.floor(r.top));
        int R = Math.min(w, (int) Math.ceil(r.right)), B = Math.min(h, (int) Math.ceil(r.bottom));
        if (R <= L || B <= T) return;
        Bitmap bm = Bitmap.createBitmap(R - L, B - T, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(bm);
        oc.translate(-L, -T);
        if (m != null) oc.concat(m);
        oc.translate(ox, oy);

        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (l.mask != null) {
            bp.setColor(Color.BLACK);
            drawShape(oc, g.body, bp);
            Paint x = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            x.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            int bw = l.mask.getWidth(), bh = l.mask.getHeight();
            float da = cwU / chU, ba = (float) bw / bh;
            Rect src;
            if (ba > da) {
                int sw = Math.round(bh * da);
                src = new Rect((bw - sw) / 2, 0, (bw + sw) / 2, bh);
            } else {
                int sh = Math.round(bw / da);
                src = new Rect(0, (bh - sh) / 2, bw, (bh + sh) / 2);
            }
            oc.drawBitmap(l.mask, src, new RectF(0, 0, cwU, chU), x);
        } else {
            bp.setShader(makeShader(l, size));
            drawShape(oc, g.body, bp);
        }
        if (!g.pat.isEmpty()) {
            Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            pp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            int baseA = Color.alpha(l.patColor);
            for (float[] it : g.pat) {
                pp.setColor(l.patColor);
                if (it[0] == 0f) {
                    pp.setStyle(Paint.Style.STROKE);
                    pp.setStrokeWidth(it[5]);
                    oc.drawLine(it[1], it[2], it[3], it[4], pp);
                } else {
                    pp.setStyle(Paint.Style.FILL);
                    pp.setAlpha(Math.round(baseA * it[4]));
                    oc.drawCircle(it[1], it[2], it[3], pp);
                }
            }
        }
        Paint comp = new Paint(Paint.FILTER_BITMAP_FLAG);
        comp.setAlpha(a);
        c.drawBitmap(bm, L, T, comp);
        bm.recycle();
        if (l.pat >= 4) {
            c.save();
            if (m != null) c.concat(m);
            c.translate(ox, oy);
            drawDetail(c, g, l, a);
            c.restore();
        }
    }

    public static Bitmap export(List<Layer> layers, int pxW, int pxH, int bg, float aw, float ah) {
        Bitmap b = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (bg != 0) c.drawColor(bg);
        float unit = Math.min(pxW, pxH);
        for (Layer l : layers) draw(c, l, unit, aw, ah, 0f, 0f, null, pxW, pxH);
        return b;
    }
}
