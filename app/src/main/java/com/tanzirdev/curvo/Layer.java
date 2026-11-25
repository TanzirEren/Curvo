package com.tanzirdev.curvo;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Random;

/** One curve/ribbon layer. Points are normalised to the canvas (0..1) but may go outside it. */
public class Layer {
    public String name = "Line";
    public boolean visible = true;
    public ArrayList<float[]> pts = new ArrayList<>();
    public int c1 = 0xFF0F9D75, c2 = 0xFF0F9D75, outlineColor = 0xFF111111;
    public float width = 0.07f, opacity = 1f, smooth = 0.5f, taper = 0f, dash = 0f, glow = 0f, outline = 0f;
    public float shadow = 0f, shDist = 0.5f, shBlur = 0.5f;
    public boolean round = false;
    public Bitmap mask;

    public Layer copy() {
        Layer l = new Layer();
        l.name = name; l.visible = visible;
        for (float[] p : pts) l.pts.add(new float[]{p[0], p[1]});
        l.c1 = c1; l.c2 = c2; l.outlineColor = outlineColor;
        l.width = width; l.opacity = opacity; l.smooth = smooth; l.taper = taper;
        l.dash = dash; l.glow = glow; l.outline = outline;
        l.shadow = shadow; l.shDist = shDist; l.shBlur = shBlur;
        l.round = round; l.mask = mask;
        return l;
    }

    public static Layer defaultLayer() {
        Layer l = new Layer();
        l.pts.add(new float[]{0.06f, 0.30f});
        l.pts.add(new float[]{0.30f, 0.68f});
        l.pts.add(new float[]{0.58f, 0.40f});
        l.pts.add(new float[]{0.94f, 0.62f});
        Presets.apply(l, Presets.ALL[0]);
        return l;
    }

    public void randomize(Random r) {
        pts.clear();
        int n = 4 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            float x = 0.06f + 0.88f * i / (n - 1);
            float y = 0.18f + 0.64f * r.nextFloat();
            pts.add(new float[]{x, y});
        }
    }
}
