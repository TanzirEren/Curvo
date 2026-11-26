package com.tanzirdev.curvo;

import java.util.ArrayList;
import java.util.List;

/** Small geometry helpers: Douglas-Peucker simplification and smoothing. */
public class Geom {

    public static ArrayList<float[]> simplify(List<float[]> pts, float eps) {
        int n = pts.size();
        if (n < 3) return new ArrayList<>(pts);
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        dp(pts, 0, n - 1, eps, keep);
        ArrayList<float[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }

    private static void dp(List<float[]> p, int a, int b, float eps, boolean[] keep) {
        if (b <= a + 1) return;
        float[] pa = p.get(a), pb = p.get(b);
        float dx = pb[0] - pa[0], dy = pb[1] - pa[1];
        float len = (float) Math.hypot(dx, dy);
        float md = -1f;
        int mi = -1;
        for (int i = a + 1; i < b; i++) {
            float[] q = p.get(i);
            float d;
            if (len < 1e-6f) d = (float) Math.hypot(q[0] - pa[0], q[1] - pa[1]);
            else d = Math.abs(dy * (q[0] - pa[0]) - dx * (q[1] - pa[1])) / len;
            if (d > md) { md = d; mi = i; }
        }
        if (md > eps && mi > 0) {
            keep[mi] = true;
            dp(p, a, mi, eps, keep);
            dp(p, mi, b, eps, keep);
        }
    }

    /** Simplifies and, if still too many points, raises the tolerance until it fits. */
    public static ArrayList<float[]> limit(List<float[]> pts, float eps, int max) {
        ArrayList<float[]> r = simplify(pts, eps);
        int guard = 0;
        while (r.size() > max && guard++ < 40) {
            eps *= 1.3f;
            r = simplify(pts, eps);
        }
        return r;
    }

    /** Moving-average smoothing that keeps the end points. Each point is {x, y, extra...}. */
    public static ArrayList<float[]> smooth(List<float[]> pts, int passes) {
        ArrayList<float[]> cur = new ArrayList<>();
        for (float[] p : pts) cur.add(p.clone());
        for (int k = 0; k < passes; k++) {
            ArrayList<float[]> nxt = new ArrayList<>();
            int n = cur.size();
            for (int i = 0; i < n; i++) {
                float[] p = cur.get(i).clone();
                if (i > 0 && i < n - 1) {
                    float[] a = cur.get(i - 1), b = cur.get(i + 1);
                    for (int j = 0; j < p.length; j++) p[j] = (a[j] + 2f * p[j] + b[j]) / 4f;
                }
                nxt.add(p);
            }
            cur = nxt;
        }
        return cur;
    }
}
