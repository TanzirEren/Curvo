package com.tanzirdev.curvo;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Random;

/** One curve/ribbon layer. Points are {x, y, widthMultiplier} in design units (canvas short side = 1). */
public class Layer {
    public String name = "Line";
    public boolean visible = true, locked = false;
    public ArrayList<float[]> pts = new ArrayList<>();
    public int c1 = 0xFF0F9D75, c2 = 0xFF0F9D75, outlineColor = 0xFF111111;
    public final ArrayList<Integer> mid = new ArrayList<>();   // extra gradient stops between c1 and c2
    public int gradMode = 0;          // 0 along the line, 1 angle, 2 radial
    public float gradAngle = 0f;
    public float width = 0.07f, opacity = 1f, smooth = 0.5f, taper = 0f, dash = 0f, glow = 0f, outline = 0f;
    public float shadow = 0f, shDist = 0.5f, shBlur = 0.5f;
    public boolean round = false;
    public int blend = 0;             // 0 normal, 1 multiply, 2 screen, 3 overlay, 4 darken, 5 lighten
    public int sym = 0;               // 0 off, 1 mirror L/R, 2 mirror T/B, 3 quad, 4 radial 6
    public int pat = 0;               // 0 none, 1 stripes, 2 dots, 3 noise, 4 road, 5 rail
    public float patScale = 0.5f;
    public int patColor = 0xFFFFFFFF;
    public float reveal = 1f;         // transient: draw-on animation progress
    public Bitmap mask;

    public static float[] pt(float x, float y) { return new float[]{x, y, 1f}; }

    public int[] colors() {
        int n = 2 + mid.size();
        int[] a = new int[n];
        a[0] = c1;
        for (int i = 0; i < mid.size(); i++) a[i + 1] = mid.get(i);
        a[n - 1] = c2;
        return a;
    }

    public Layer copy() {
        Layer l = new Layer();
        l.name = name; l.visible = visible; l.locked = locked;
        for (float[] p : pts) l.pts.add(new float[]{p[0], p[1], p.length > 2 ? p[2] : 1f});
        l.c1 = c1; l.c2 = c2; l.outlineColor = outlineColor;
        l.mid.addAll(mid);
        l.gradMode = gradMode; l.gradAngle = gradAngle;
        l.width = width; l.opacity = opacity; l.smooth = smooth; l.taper = taper;
        l.dash = dash; l.glow = glow; l.outline = outline;
        l.shadow = shadow; l.shDist = shDist; l.shBlur = shBlur;
        l.round = round; l.blend = blend; l.sym = sym;
        l.pat = pat; l.patScale = patScale; l.patColor = patColor;
        l.reveal = reveal;
        l.mask = mask;
        return l;
    }

    public static Layer defaultLayer() {
        Layer l = new Layer();
        l.pts.add(pt(0.06f, 0.30f));
        l.pts.add(pt(0.30f, 0.68f));
        l.pts.add(pt(0.58f, 0.40f));
        l.pts.add(pt(0.94f, 0.62f));
        Presets.apply(l, Presets.ALL[0]);
        return l;
    }

    public void randomize(Random r, float aw, float ah) {
        pts.clear();
        int n = 4 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            float x = aw * (0.06f + 0.88f * i / (n - 1));
            float y = ah * (0.18f + 0.64f * r.nextFloat());
            pts.add(pt(x, y));
        }
    }
}
