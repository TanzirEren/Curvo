package com.tanzirdev.curvo;

import android.graphics.Bitmap;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

/** Writes the design as a real vector SVG (gradients, masks, patterns, blur and blend modes included). */
public class SvgWriter {
    private static final String[] BLEND = {"normal", "multiply", "screen", "overlay", "darken", "lighten"};

    private static String f(float v) {
        float r = Math.round(v * 10f) / 10f;
        if (r == (int) r) return String.valueOf((int) r);
        return String.valueOf(r);
    }

    private static String f3(float v) { return String.format(Locale.US, "%.3f", v); }
    private static String hex(int c) { return String.format(Locale.US, "#%06X", c & 0xFFFFFF); }

    private static String polyData(float[] q) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < q.length; i += 2) {
            sb.append(i == 0 ? "M" : "L").append(f(q[i])).append(' ').append(f(q[i + 1])).append(' ');
        }
        return sb.append("Z ").toString();
    }

    private static String circData(float[] c) {
        float x = c[0], y = c[1], r = c[2];
        return "M" + f(x - r) + " " + f(y) + " a" + f(r) + " " + f(r) + " 0 1 0 " + f(2 * r) + " 0 a"
                + f(r) + " " + f(r) + " 0 1 0 " + f(-2 * r) + " 0 Z ";
    }

    private static String pathData(Renderer.Shape s) {
        StringBuilder sb = new StringBuilder();
        for (float[] q : s.polys) sb.append(polyData(q));
        for (float[] c : s.circs) sb.append(circData(c));
        return sb.toString();
    }

    private static String b64(Bitmap b) {
        Bitmap s = b;
        int max = Math.max(b.getWidth(), b.getHeight());
        if (max > 1024) {
            float k = 1024f / max;
            s = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)), Math.max(1, Math.round(b.getHeight() * k)), true);
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        s.compress(Bitmap.CompressFormat.PNG, 100, bo);
        if (s != b) s.recycle();
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    public static String build(List<Layer> layers, int W, int H, int bg, float aw, float ah) {
        float unit = Math.min(W, H);
        StringBuilder defs = new StringBuilder(), body = new StringBuilder();
        IdentityHashMap<Bitmap, String> imgs = new IdentityHashMap<>();
        int[] idc = {0};
        if (bg != 0) {
            body.append("<rect width=\"").append(W).append("\" height=\"").append(H).append("\" fill=\"").append(hex(bg)).append("\"");
            int a = (bg >>> 24);
            if (a < 255) body.append(" fill-opacity=\"").append(f3(a / 255f)).append("\"");
            body.append("/>\n");
        }
        for (Layer l : layers) {
            if (!l.visible || l.pts.size() < 2) continue;
            ArrayList<Layer> copies = Renderer.symCopies(l, aw, ah);
            body.append("<g");
            if (l.blend > 0 && l.blend < BLEND.length) body.append(" style=\"mix-blend-mode:").append(BLEND[l.blend]).append("\"");
            body.append(">\n");
            for (Layer k : copies) layerSvg(k, unit, defs, body, imgs, idc, W, H);
            body.append("</g>\n");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"")
          .append(W).append("\" height=\"").append(H).append("\" viewBox=\"0 0 ").append(W).append(' ').append(H).append("\">\n");
        sb.append("<defs>\n").append(defs).append("</defs>\n").append(body).append("</svg>\n");
        return sb.toString();
    }

    private static String clipDef(Renderer.Geo g, StringBuilder defs, int[] idc) {
        String cid = "c" + (++idc[0]);
        defs.append("<clipPath id=\"").append(cid).append("\">");
        for (float[] q : g.body.polys) defs.append("<path d=\"").append(polyData(q)).append("\"/>");
        for (float[] c : g.body.circs)
            defs.append("<circle cx=\"").append(f(c[0])).append("\" cy=\"").append(f(c[1])).append("\" r=\"").append(f(c[2])).append("\"/>");
        defs.append("</clipPath>\n");
        return cid;
    }

    private static void layerSvg(Layer l, float unit, StringBuilder defs, StringBuilder out,
                                 IdentityHashMap<Bitmap, String> imgs, int[] idc, int W, int H) {
        Renderer.Geo g = Renderer.build(l, unit);
        if (!g.valid) return;
        float a = Math.max(0f, Math.min(1f, l.opacity));
        out.append("<g");
        if (a < 0.999f) out.append(" opacity=\"").append(f3(a)).append("\"");
        out.append(">\n");

        if (g.shadow != null) {
            int id = ++idc[0];
            float sigma = Math.max(1f, (0.004f + l.shBlur * 0.05f) * unit) / 2f;
            defs.append("<filter id=\"f").append(id).append("\" x=\"-30%\" y=\"-30%\" width=\"160%\" height=\"160%\"><feGaussianBlur stdDeviation=\"")
                .append(f(sigma)).append("\"/></filter>\n");
            out.append("<path transform=\"translate(").append(f(l.shDist * unit * 0.03f)).append(' ').append(f(l.shDist * unit * 0.05f))
               .append(")\" fill=\"#000000\" fill-opacity=\"").append(f3(0.65f * Math.min(1f, l.shadow)))
               .append("\" filter=\"url(#f").append(id).append(")\" d=\"").append(pathData(g.shadow)).append("\"/>\n");
        }
        if (g.glow != null) {
            int id = ++idc[0];
            float sigma = Math.max(1f, l.glow * unit * 0.05f) / 2f;
            defs.append("<filter id=\"f").append(id).append("\" x=\"-30%\" y=\"-30%\" width=\"160%\" height=\"160%\"><feGaussianBlur stdDeviation=\"")
                .append(f(sigma)).append("\"/></filter>\n");
            out.append("<path fill=\"").append(hex(l.c1)).append("\" fill-opacity=\"0.9\" filter=\"url(#f").append(id)
               .append(")\" d=\"").append(pathData(g.glow)).append("\"/>\n");
        }
        if (g.outline != null) {
            out.append("<path fill=\"").append(hex(l.outlineColor)).append("\" d=\"").append(pathData(g.outline)).append("\"/>\n");
        }

        String clip = null;
        if (l.mask != null) {
            String iid = imgs.get(l.mask);
            if (iid == null) {
                iid = "i" + (++idc[0]);
                imgs.put(l.mask, iid);
                defs.append("<image id=\"").append(iid).append("\" x=\"0\" y=\"0\" width=\"").append(W).append("\" height=\"").append(H)
                    .append("\" preserveAspectRatio=\"xMidYMid slice\" xlink:href=\"data:image/png;base64,")
                    .append(b64(l.mask)).append("\"/>\n");
            }
            clip = clipDef(g, defs, idc);
            out.append("<use xlink:href=\"#").append(iid).append("\" clip-path=\"url(#").append(clip).append(")\"/>\n");
        } else {
            int[] cols = l.colors();
            float[] gs = Renderer.gradientSpec(l, unit);
            String gid = "g" + (++idc[0]);
            if (gs[0] == 1f) {
                defs.append("<radialGradient id=\"").append(gid).append("\" gradientUnits=\"userSpaceOnUse\" cx=\"").append(f(gs[1]))
                    .append("\" cy=\"").append(f(gs[2])).append("\" r=\"").append(f(gs[3])).append("\">");
            } else {
                defs.append("<linearGradient id=\"").append(gid).append("\" gradientUnits=\"userSpaceOnUse\" x1=\"").append(f(gs[1]))
                    .append("\" y1=\"").append(f(gs[2])).append("\" x2=\"").append(f(gs[3])).append("\" y2=\"").append(f(gs[4])).append("\">");
            }
            for (int i = 0; i < cols.length; i++) {
                defs.append("<stop offset=\"").append(f3(i / (float) (cols.length - 1))).append("\" stop-color=\"").append(hex(cols[i]))
                    .append("\" stop-opacity=\"").append(f3((cols[i] >>> 24) / 255f)).append("\"/>");
            }
            defs.append(gs[0] == 1f ? "</radialGradient>\n" : "</linearGradient>\n");
            out.append("<path fill=\"url(#").append(gid).append(")\" d=\"").append(pathData(g.body)).append("\"/>\n");
        }

        if (!g.pat.isEmpty()) {
            if (clip == null) clip = clipDef(g, defs, idc);
            out.append("<g clip-path=\"url(#").append(clip).append(")\" fill=\"").append(hex(l.patColor)).append("\" stroke=\"")
               .append(hex(l.patColor)).append("\">\n");
            for (float[] it : g.pat) {
                if (it[0] == 0f) {
                    out.append("<line x1=\"").append(f(it[1])).append("\" y1=\"").append(f(it[2])).append("\" x2=\"").append(f(it[3]))
                       .append("\" y2=\"").append(f(it[4])).append("\" stroke-width=\"").append(f(it[5])).append("\"/>");
                } else {
                    out.append("<circle stroke=\"none\" cx=\"").append(f(it[1])).append("\" cy=\"").append(f(it[2])).append("\" r=\"")
                       .append(f(it[3])).append("\" fill-opacity=\"").append(f3(it[4])).append("\"/>");
                }
            }
            out.append("\n</g>\n");
        }
        if (!g.detail.polys.isEmpty()) {
            out.append("<path fill=\"").append(hex(l.patColor)).append("\" d=\"").append(pathData(g.detail)).append("\"/>\n");
        }
        out.append("</g>\n");
    }
}
