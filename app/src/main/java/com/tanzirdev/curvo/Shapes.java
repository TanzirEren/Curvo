package com.tanzirdev.curvo;

/** Ready-made curve shapes (point sets). */
public class Shapes {
    public static class S {
        public final String name;
        public final float[][] p;
        public final float smooth;
        S(String n, float[][] p, float smooth) { this.name = n; this.p = p; this.smooth = smooth; }
    }

    public static final S[] ALL = {
        new S("S-Curve", new float[][]{{.08f,.28f},{.33f,.30f},{.50f,.50f},{.67f,.70f},{.92f,.72f}}, 0.5f),
        new S("Wave",    new float[][]{{.06f,.50f},{.22f,.28f},{.39f,.72f},{.56f,.28f},{.73f,.72f},{.94f,.50f}}, 0.5f),
        new S("Arc",     new float[][]{{.10f,.75f},{.25f,.40f},{.50f,.25f},{.75f,.40f},{.90f,.75f}}, 0.5f),
        new S("Zigzag",  new float[][]{{.10f,.70f},{.30f,.30f},{.50f,.70f},{.70f,.30f},{.90f,.70f}}, 0f),
        new S("Rise",    new float[][]{{.08f,.82f},{.35f,.70f},{.55f,.45f},{.75f,.25f},{.92f,.18f}}, 0.5f),
        new S("Drop",    new float[][]{{.30f,.05f},{.38f,.30f},{.55f,.62f},{.78f,.92f}}, 0.5f),
        new S("Big S",   new float[][]{{.12f,.12f},{.50f,.18f},{.50f,.82f},{.88f,.88f}}, 0.6f),
        new S("Spiral",  new float[][]{{.50f,.50f},{.62f,.50f},{.62f,.62f},{.38f,.62f},{.38f,.38f},{.74f,.38f},{.74f,.74f}}, 0.75f),
        new S("Swirl",   new float[][]{{.20f,.50f},{.40f,.20f},{.70f,.30f},{.70f,.70f},{.40f,.80f},{.30f,.50f}}, 0.8f),
    };

    /** Fits the shape to the canvas (aw x ah units). */
    public static void apply(Layer l, S s, float aw, float ah) {
        l.pts.clear();
        for (float[] q : s.p) l.pts.add(new float[]{q[0] * aw, q[1] * ah});
        l.smooth = s.smooth;
    }
}
