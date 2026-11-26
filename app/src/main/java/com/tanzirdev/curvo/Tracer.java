package com.tanzirdev.curvo;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Turns a PNG/photo that contains thick lines into editable curve points:
 * threshold -> connected parts -> thinning (skeleton) -> longest path -> simplify.
 * Thickness along the line becomes the per-point width.
 */
public class Tracer {

    public static class Line {
        public final ArrayList<float[]> pts = new ArrayList<>();   // {x, y, widthMultiplier} in grid pixels
        public float thick;                                       // average thickness in grid pixels
        public int color;
        public int gw, gh;                                        // grid size
    }

    public static ArrayList<Line> trace(Bitmap src, int maxLines, float eps) {
        ArrayList<Line> out = new ArrayList<>();
        int sw = src.getWidth(), sh = src.getHeight();
        float sc = Math.min(1f, 300f / Math.max(sw, sh));
        int w = Math.max(8, Math.round(sw * sc)), h = Math.max(8, Math.round(sh * sc));
        Bitmap sm = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        sm.getPixels(px, 0, w, 0, 0, w, h);
        if (sm != src) sm.recycle();

        boolean[] fg = new boolean[w * h];
        int transparent = 0;
        for (int p : px) if ((p >>> 24) < 200) transparent++;
        if (transparent > w * h * 0.01) {
            for (int i = 0; i < px.length; i++) fg[i] = (px[i] >>> 24) > 128;
        } else {
            int[] cs = {px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1]};
            int br = 0, bg = 0, bb = 0;
            for (int c : cs) { br += (c >> 16) & 255; bg += (c >> 8) & 255; bb += c & 255; }
            br /= 4; bg /= 4; bb /= 4;
            for (int i = 0; i < px.length; i++) {
                int dr = ((px[i] >> 16) & 255) - br, dg = ((px[i] >> 8) & 255) - bg, db = (px[i] & 255) - bb;
                fg[i] = dr * dr + dg * dg + db * db > 70 * 70;
            }
        }

        // connected parts (8-neighbour)
        int[] label = new int[w * h];
        int[] queue = new int[w * h];
        ArrayList<int[]> comps = new ArrayList<>();
        int nl = 0;
        for (int i = 0; i < fg.length; i++) {
            if (!fg[i] || label[i] != 0) continue;
            nl++;
            int qh = 0, qt = 0;
            queue[qt++] = i;
            label[i] = nl;
            while (qh < qt) {
                int cur = queue[qh++];
                int cx = cur % w, cy = cur / w;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = cx + dx, ny = cy + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int ni = ny * w + nx;
                        if (fg[ni] && label[ni] == 0) { label[ni] = nl; queue[qt++] = ni; }
                    }
                }
            }
            comps.add(new int[]{nl, qt});
        }
        Collections.sort(comps, (a, b) -> b[1] - a[1]);

        int minSize = Math.max(30, (int) (w * h * 0.0015f));
        for (int ci = 0; ci < comps.size() && out.size() < maxLines; ci++) {
            if (comps.get(ci)[1] < minSize) break;
            Line ln = traceComp(px, label, comps.get(ci)[0], w, h, eps);
            if (ln != null) out.add(ln);
        }
        return out;
    }

    private static Line traceComp(int[] px, int[] label, int lab, int w, int h, float eps) {
        boolean[] m = new boolean[w * h];
        long rs = 0, gs = 0, bs = 0;
        int cnt = 0;
        for (int i = 0; i < m.length; i++) {
            if (label[i] != lab) continue;
            m[i] = true;
            rs += (px[i] >> 16) & 255; gs += (px[i] >> 8) & 255; bs += px[i] & 255;
            cnt++;
        }
        if (cnt == 0) return null;
        boolean[] sk = m.clone();
        thin(sk, w, h);
        float[] dt = distance(m, w, h);

        int start = -1;
        for (int i = 0; i < sk.length; i++) if (sk[i]) { start = i; break; }
        if (start < 0) return null;
        int a = farthest(sk, w, h, start, null);
        int[] prev = new int[w * h];
        int b = farthest(sk, w, h, a, prev);
        ArrayList<Integer> path = new ArrayList<>();
        for (int c = b; c != -1; c = prev[c]) path.add(c);
        int n = path.size();
        if (n < 3) return null;

        float[] d = new float[n];
        for (int i = 0; i < n; i++) {
            float s = 0f;
            int k = 0;
            for (int j = Math.max(0, i - 3); j <= Math.min(n - 1, i + 3); j++) { s += dt[path.get(j)]; k++; }
            d[i] = s / k;
        }
        float avg = 0f;
        for (float v : d) avg += v;
        avg = Math.max(1.5f, 2f * avg / n);

        ArrayList<float[]> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int idx = path.get(i);
            pts.add(new float[]{idx % w + 0.5f, idx / w + 0.5f, (2f * d[i]) / avg});
        }
        // thinning shortens the ends by about half the thickness: extend them back
        extend(pts, 0, Math.min(4, n - 1), d[0], w, h);
        extend(pts, pts.size() - 1, Math.max(0, pts.size() - 5), d[n - 1], w, h);

        ArrayList<float[]> simp = Geom.limit(pts, eps, 28);
        if (simp.size() < 2) return null;
        Line ln = new Line();
        for (float[] p : simp) ln.pts.add(new float[]{p[0], p[1], Math.max(0.3f, Math.min(2.5f, p[2]))});
        ln.thick = avg;
        ln.color = 0xFF000000 | ((int) (rs / cnt) << 16) | ((int) (gs / cnt) << 8) | (int) (bs / cnt);
        ln.gw = w;
        ln.gh = h;
        return ln;
    }

    private static void extend(ArrayList<float[]> pts, int endIdx, int refIdx, float dist, int w, int h) {
        float[] e = pts.get(endIdx), r = pts.get(refIdx);
        float dx = e[0] - r[0], dy = e[1] - r[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) return;
        e[0] = Math.max(0f, Math.min(w, e[0] + dx / len * dist));
        e[1] = Math.max(0f, Math.min(h, e[1] + dy / len * dist));
    }

    /** BFS over skeleton pixels; returns the farthest pixel. If prev != null it receives parents (-1 for start). */
    private static int farthest(boolean[] sk, int w, int h, int start, int[] prev) {
        int[] dist = new int[w * h];
        java.util.Arrays.fill(dist, -1);
        int[] q = new int[w * h];
        int qh = 0, qt = 0;
        q[qt++] = start;
        dist[start] = 0;
        if (prev != null) prev[start] = -1;
        int last = start;
        while (qh < qt) {
            int cur = q[qh++];
            last = cur;
            int cx = cur % w, cy = cur / w;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = cx + dx, ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int ni = ny * w + nx;
                    if (sk[ni] && dist[ni] < 0) {
                        dist[ni] = dist[cur] + 1;
                        if (prev != null) prev[ni] = cur;
                        q[qt++] = ni;
                    }
                }
            }
        }
        return last;
    }

    /** Zhang-Suen thinning. */
    private static void thin(boolean[] m, int w, int h) {
        ArrayList<Integer> del = new ArrayList<>();
        boolean[] s = new boolean[9];
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 80) {
            changed = false;
            for (int pass = 0; pass < 2; pass++) {
                del.clear();
                for (int y = 1; y < h - 1; y++) {
                    for (int x = 1; x < w - 1; x++) {
                        int i = y * w + x;
                        if (!m[i]) continue;
                        boolean p2 = m[i - w], p3 = m[i - w + 1], p4 = m[i + 1], p5 = m[i + w + 1];
                        boolean p6 = m[i + w], p7 = m[i + w - 1], p8 = m[i - 1], p9 = m[i - w - 1];
                        int bn = (p2 ? 1 : 0) + (p3 ? 1 : 0) + (p4 ? 1 : 0) + (p5 ? 1 : 0)
                               + (p6 ? 1 : 0) + (p7 ? 1 : 0) + (p8 ? 1 : 0) + (p9 ? 1 : 0);
                        if (bn < 2 || bn > 6) continue;
                        s[0] = p2; s[1] = p3; s[2] = p4; s[3] = p5; s[4] = p6; s[5] = p7; s[6] = p8; s[7] = p9; s[8] = p2;
                        int tr = 0;
                        for (int k = 0; k < 8; k++) if (!s[k] && s[k + 1]) tr++;
                        if (tr != 1) continue;
                        if (pass == 0) {
                            if ((p2 && p4 && p6) || (p4 && p6 && p8)) continue;
                        } else {
                            if ((p2 && p4 && p8) || (p2 && p6 && p8)) continue;
                        }
                        del.add(i);
                    }
                }
                for (int i : del) m[i] = false;
                if (!del.isEmpty()) changed = true;
            }
        }
    }

    /** Chamfer distance to the nearest background pixel (image border counts as background). */
    private static float[] distance(boolean[] m, int w, int h) {
        float inf = 1e9f;
        float[] d = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!m[i]) d[i] = 0f;
                else d[i] = (x == 0 || y == 0 || x == w - 1 || y == h - 1) ? 1f : inf;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!m[i]) continue;
                float v = d[i];
                if (x > 0) v = Math.min(v, d[i - 1] + 1f);
                if (y > 0) {
                    v = Math.min(v, d[i - w] + 1f);
                    if (x > 0) v = Math.min(v, d[i - w - 1] + 1.414f);
                    if (x < w - 1) v = Math.min(v, d[i - w + 1] + 1.414f);
                }
                d[i] = v;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                if (!m[i]) continue;
                float v = d[i];
                if (x < w - 1) v = Math.min(v, d[i + 1] + 1f);
                if (y < h - 1) {
                    v = Math.min(v, d[i + w] + 1f);
                    if (x < w - 1) v = Math.min(v, d[i + w + 1] + 1.414f);
                    if (x > 0) v = Math.min(v, d[i + w - 1] + 1.414f);
                }
                d[i] = v;
            }
        }
        for (int i = 0; i < d.length; i++) if (d[i] > 1e8f) d[i] = 1f;
        return d;
    }
}
