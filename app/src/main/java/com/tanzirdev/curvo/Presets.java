package com.tanzirdev.curvo;

/** 26 ready-made ribbon styles. */
public class Presets {
    public static class P {
        public final String name;
        public final int c1, c2, oc;
        public final float width, taper, dash, glow, outline;
        public final boolean round;
        P(String n, int c1, int c2, float w, float taper, float dash, float glow, float outline, boolean round, int oc) {
            this.name = n; this.c1 = c1; this.c2 = c2; this.width = w; this.taper = taper;
            this.dash = dash; this.glow = glow; this.outline = outline; this.round = round; this.oc = oc;
        }
    }

    static P s(String n, int a, int b, float w) { return new P(n, a, b, w, 0, 0, 0, 0, false, 0xFF111111); }

    public static final P[] ALL = {
        s("Emerald",  0xFF0F9D75, 0xFF0F9D75, 0.07f),
        s("Mint",     0xFF7EE8C0, 0xFF2FBF9A, 0.07f),
        s("Forest",   0xFF14532D, 0xFF22C55E, 0.07f),
        s("Ocean",    0xFF0EA5E9, 0xFF2563EB, 0.07f),
        s("Sky",      0xFF7DD3FC, 0xFFBAE6FD, 0.08f),
        s("Sunset",   0xFFF97316, 0xFFEC4899, 0.07f),
        s("Rose",     0xFFFB7185, 0xFFF43F5E, 0.07f),
        s("Lavender", 0xFFC4B5FD, 0xFF8B5CF6, 0.07f),
        s("Gold",     0xFFFACC15, 0xFFD97706, 0.07f),
        s("Midnight", 0xFF000000, 0xFF000000, 0.09f),
        s("Slate",    0xFF64748B, 0xFF0F172A, 0.07f),
        new P("Neon",  0xFF22FFAA, 0xFF00E5FF, 0.035f, 0, 0, 0.7f, 0, false, 0xFF111111),
        s("Candy",    0xFFFF80AB, 0xFF80D8FF, 0.07f),
        s("Aurora",   0xFF34D399, 0xFF8B5CF6, 0.07f),
        s("Peach",    0xFFFFB38A, 0xFFFF6B6B, 0.07f),
        s("Lime",     0xFFA3E635, 0xFF16A34A, 0.07f),
        s("Berry",    0xFF9D174D, 0xFFDB2777, 0.07f),
        s("Coffee",   0xFF78350F, 0xFFD6A77A, 0.07f),
        new P("Fire",  0xFFEF4444, 0xFFFACC15, 0.09f, 0.55f, 0, 0, 0, false, 0xFF111111),
        new P("Ice",   0xFFE0F2FE, 0xFF38BDF8, 0.06f, 0, 0, 0.35f, 0, false, 0xFF111111),
        new P("Dashed", 0xFF0F9D75, 0xFF0F9D75, 0.035f, 0, 0.5f, 0, 0, false, 0xFF111111),
        new P("Dotted", 0xFF0F172A, 0xFF0F172A, 0.03f, 0, 0.04f, 0, 0, true, 0xFF111111),
        new P("Outlined", 0xFFFFFFFF, 0xFFA7F3D0, 0.06f, 0, 0, 0, 0.6f, false, 0xFF0F172A),
        new P("Brush", 0xFF000000, 0xFF000000, 0.10f, 0.85f, 0, 0, 0, false, 0xFF111111),
        s("Thin",     0xFF111111, 0xFF111111, 0.012f),
        new P("Duo",   0xFFFFFFFF, 0xFFFFFFFF, 0.05f, 0, 0, 0, 0.7f, false, 0xFF0F9D75),
    };

    public static void apply(Layer l, P p) {
        l.c1 = p.c1; l.c2 = p.c2; l.outlineColor = p.oc;
        l.width = p.width; l.taper = p.taper; l.dash = p.dash;
        l.glow = p.glow; l.outline = p.outline; l.round = p.round;
        l.mid.clear();
        l.gradMode = 0;
    }
}
