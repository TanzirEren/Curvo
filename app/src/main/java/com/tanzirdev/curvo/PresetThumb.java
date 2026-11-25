package com.tanzirdev.curvo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

/** Small rounded tile that previews a style preset or a shape. */
public class PresetThumb extends View {
    private final Layer l = new Layer();
    private final Path clip = new Path();
    private final float dp;

    public PresetThumb(Context c, Presets.P p) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        l.pts.add(new float[]{0.12f, 0.30f});
        l.pts.add(new float[]{0.38f, 0.70f});
        l.pts.add(new float[]{0.62f, 0.34f});
        l.pts.add(new float[]{0.90f, 0.68f});
        Presets.apply(l, p);
        if (p.dash == 0f) l.width = Math.max(l.width, 0.045f);
    }

    public PresetThumb(Context c, Shapes.S s) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        for (float[] q : s.p) l.pts.add(new float[]{q[0], q[1]});
        l.smooth = s.smooth;
        Presets.apply(l, Presets.ALL[0]);
        l.width = 0.05f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = getWidth();
        clip.reset();
        clip.addRoundRect(0, 0, s, s, 18 * dp, 18 * dp, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawColor(0xFFEAF3EE);
        Renderer.draw(canvas, l, s);
        canvas.restore();
    }
}
