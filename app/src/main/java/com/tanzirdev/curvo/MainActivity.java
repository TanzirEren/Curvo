package com.tanzirdev.curvo;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.graphics.Path;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    static final int DARK = 0xFF0B3D2E, GREEN = 0xFF0F9D75, MUTED = 0xFF5B7F72, REQ_MASK = 11;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT, MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int[] PAL = {
        0xFF000000, 0xFFFFFFFF, 0xFF64748B, 0xFF0F172A, 0xFF0F9D75, 0xFF34D399, 0xFFA3E635, 0xFFFACC15,
        0xFFF97316, 0xFFEF4444, 0xFFEC4899, 0xFF8B5CF6, 0xFF2563EB, 0xFF0EA5E9, 0xFF14B8A6, 0xFF78350F};
    static final int[] BG = {
        0, 0xFFFFFFFF, 0xFF000000, 0xFFFFF7ED, 0xFFECFDF5, 0xFFEFF6FF, 0xFFFDF2F8, 0xFFF5F3FF,
        0xFF0F172A, 0xFF14532D, 0xFF78350F, 0xFFFACC15, 0xFFF97316, 0xFFEF4444, 0xFF2563EB, 0xFF8B5CF6};

    interface Getter { float g(Layer l); }
    interface Setter { void s(Layer l, float v); }
    static class Row { SeekBar sb; Getter g; TextView val; }
    static class Snap {
        ArrayList<Layer> l; int a, cw, ch;
        Snap(ArrayList<Layer> src, int a, int cw, int ch) { l = cloneAll(src); this.a = a; this.cw = cw; this.ch = ch; }
    }

    CurveView cv;
    ImageButton bUndo, bRedo, bLayers, bExport, bVector, bPreset, bColor, bEdit, bMask,
            bShapes, bTransform, bFx, bBackdrop, bRatio, bGrid, bAddLayer;
    View pPreset, pColor, pEdit, pLayers, pShapes;
    LinearLayout pTransform, pBackdrop, pFx, pRatio;
    LinearLayout presetRow, shapeRow, colorBox, editBox, layerList;
    TextView chipRound;
    int curPanel = -1, target = 0;
    float density;
    boolean exporting = false;
    final ArrayList<Snap> undoS = new ArrayList<>(), redoS = new ArrayList<>();
    final ArrayList<Row> rows = new ArrayList<>();
    final Random rnd = new Random();

    // colour panel state
    final GradientDrawable[] dotDraw = new GradientDrawable[3];
    final LinearLayout[] pills = new LinearLayout[3];
    View previewBar;
    TextView hexText;
    SeekBar sbHue, sbSat, sbVal;
    GradientDrawable gSat, gVal;
    // ratio state
    static final String[] R_NAME = {"1:1", "4:5", "9:16", "16:9", "3:4", "4:3", "2:3", "3:2", "21:9", "A4"};
    static final int[] R_W = {1, 4, 9, 16, 3, 4, 2, 3, 21, 210};
    static final int[] R_H = {1, 5, 16, 9, 4, 3, 3, 2, 9, 297};
    static final String[] R_SUB = {"Square", "Feed", "Story", "Wide", "Portrait", "Classic", "Pinterest", "Photo", "Cinema", "Print"};
    EditText edW, edH;
    TextView ratioInfo, lockChip;
    boolean lockRatio = false, ignoreWatch = false;
    final ArrayList<RatioView> ratioViews = new ArrayList<>();
    final ArrayList<LinearLayout> ratioTiles = new ArrayList<>();
    // backdrop state
    final ArrayList<View> bgSwatches = new ArrayList<>();

    static ArrayList<Layer> cloneAll(ArrayList<Layer> src) {
        ArrayList<Layer> out = new ArrayList<>();
        for (Layer l : src) out.add(l.copy());
        return out;
    }

    int dp(float v) { return (int) (v * density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        cv = findViewById(R.id.canvas);
        bUndo = findViewById(R.id.bUndo); bRedo = findViewById(R.id.bRedo);
        bLayers = findViewById(R.id.bLayers); bExport = findViewById(R.id.bExport);
        bVector = findViewById(R.id.bVector); bPreset = findViewById(R.id.bPreset);
        bColor = findViewById(R.id.bColor); bEdit = findViewById(R.id.bEdit);
        bMask = findViewById(R.id.bMask); bShapes = findViewById(R.id.bShapes);
        bTransform = findViewById(R.id.bTransform); bFx = findViewById(R.id.bFx);
        bBackdrop = findViewById(R.id.bBackdrop); bGrid = findViewById(R.id.bGrid);
        bRatio = findViewById(R.id.bRatio);
        bAddLayer = findViewById(R.id.bAddLayer);
        pPreset = findViewById(R.id.pPreset); pColor = findViewById(R.id.pColor);
        pEdit = findViewById(R.id.pEdit); pLayers = findViewById(R.id.pLayers);
        pShapes = findViewById(R.id.pShapes);
        pTransform = findViewById(R.id.pTransform); pBackdrop = findViewById(R.id.pBackdrop);
        pFx = findViewById(R.id.pFx);
        pRatio = findViewById(R.id.pRatio);
        presetRow = findViewById(R.id.presetRow); shapeRow = findViewById(R.id.shapeRow);
        colorBox = findViewById(R.id.colorBox); editBox = findViewById(R.id.editBox);
        layerList = findViewById(R.id.layerList);

        ImageButton[] all = {bUndo, bRedo, bLayers, bExport, bVector, bPreset, bColor, bEdit, bMask,
                bShapes, bTransform, bFx, bBackdrop, bRatio, bGrid, bAddLayer};
        for (ImageButton ib : all) ib.setColorFilter(DARK);

        loadSession();
        if (cv.layers.isEmpty()) { cv.layers.add(Layer.defaultLayer()); cv.active = 0; }

        cv.listener = new CurveView.Listener() {
            @Override public void onBeforeChange() { push(); }
            @Override public void onChanged() { }
            @Override public void onMessage(String m) {
                Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
            }
        };

        buildPresets();
        buildShapes();
        buildColorPanel();
        buildEditPanel();
        buildTransformPanel();
        buildBackdropPanel();
        buildFxPanel();
        buildRatioPanel();
        refreshLayers();
        refreshEdit();
        updateUndo();
        updateRatioUI();
        highlight(bVector, cv.vectorMode);
        highlight(bGrid, cv.gridMode > 0);

        bUndo.setOnClickListener(v -> undo());
        bRedo.setOnClickListener(v -> redo());
        bLayers.setOnClickListener(v -> showPanel(3));
        bExport.setOnClickListener(v -> showExport());
        bPreset.setOnClickListener(v -> showPanel(0));
        bColor.setOnClickListener(v -> showPanel(1));
        bEdit.setOnClickListener(v -> showPanel(2));
        bShapes.setOnClickListener(v -> showPanel(4));
        bTransform.setOnClickListener(v -> showPanel(5));
        bBackdrop.setOnClickListener(v -> showPanel(6));
        bFx.setOnClickListener(v -> showPanel(7));
        bRatio.setOnClickListener(v -> showPanel(8));
        bVector.setOnClickListener(v -> {
            cv.vectorMode = !cv.vectorMode;
            highlight(bVector, cv.vectorMode);
            cv.invalidate();
        });
        bGrid.setOnClickListener(v -> {
            cv.gridMode = (cv.gridMode + 1) % 3;
            highlight(bGrid, cv.gridMode > 0);
            String[] t = {"Grid off", "Grid", "Grid + Snap"};
            Toast.makeText(this, t[cv.gridMode], Toast.LENGTH_SHORT).show();
            cv.invalidate();
        });
        bMask.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(i, REQ_MASK);
        });
        bMask.setOnLongClickListener(v -> { clearMask(); return true; });
        bAddLayer.setOnClickListener(v -> addLayer());
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
    }

    // ---------------------------------------------------------------- helpers
    Layer cur() {
        if (cv.layers.isEmpty()) return null;
        cv.active = Math.max(0, Math.min(cv.active, cv.layers.size() - 1));
        return cv.layers.get(cv.active);
    }

    void highlight(ImageButton b, boolean on) {
        b.setColorFilter(on ? GREEN : DARK);
        b.setBackgroundResource(on ? R.drawable.icon_sel : R.drawable.btn_ripple);
    }

    void showPanel(int id) {
        View[] ps = {pPreset, pColor, pEdit, pLayers, pShapes, pTransform, pBackdrop, pFx, pRatio};
        ImageButton[] bs = {bPreset, bColor, bEdit, bLayers, bShapes, bTransform, bBackdrop, bFx, bRatio};
        for (View p : ps) p.setVisibility(View.GONE);
        if (id == curPanel) {
            curPanel = -1;
        } else {
            curPanel = id;
            View p = ps[id];
            p.setVisibility(View.VISIBLE);
            p.setAlpha(0f);
            p.setTranslationY(dp(28));
            p.animate().alpha(1f).translationY(0f).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
            if (id == 1) syncColorUI();
            if (id == 8) updateRatioUI();
        }
        for (int i = 0; i < bs.length; i++) highlight(bs[i], i == curPanel);
    }

    void pop(View v) {
        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(90)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
    }

    void addHandle(LinearLayout parent) {
        View h = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(2));
        g.setColor(0x330B3D2E);
        h.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(6);
        h.setLayoutParams(lp);
        parent.addView(h);
    }

    TextView header(LinearLayout parent, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(11);
        v.setTextColor(GREEN);
        v.setTypeface(null, Typeface.BOLD);
        v.setLetterSpacing(0.12f);
        v.setPadding(dp(2), dp(10), 0, dp(2));
        parent.addView(v);
        return v;
    }

    TextView chip(String t, View.OnClickListener c) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(DARK);
        v.setTextSize(12);
        v.setPadding(dp(12), dp(8), dp(12), dp(8));
        v.setBackgroundResource(R.drawable.glass_chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        v.setLayoutParams(lp);
        v.setOnClickListener(c);
        return v;
    }

    TextView gchip(String t, View.OnClickListener c) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(DARK);
        v.setTextSize(12);
        v.setPadding(dp(6), dp(11), dp(6), dp(11));
        v.setBackgroundResource(R.drawable.glass_chip);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        v.setLayoutParams(lp);
        v.setOnClickListener(c);
        return v;
    }

    GradientDrawable circle(int color, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        if (selected) g.setStroke(dp(3), GREEN); else g.setStroke(dp(1), 0x33000000);
        return g;
    }

    // ---------------------------------------------------------------- undo / redo
    void push() {
        undoS.add(new Snap(cv.layers, cv.active, cv.canvasW, cv.canvasH));
        if (undoS.size() > 60) undoS.remove(0);
        redoS.clear();
        updateUndo();
    }

    void undo() {
        if (undoS.isEmpty()) return;
        redoS.add(new Snap(cv.layers, cv.active, cv.canvasW, cv.canvasH));
        Snap s = undoS.remove(undoS.size() - 1);
        cv.layers = s.l; cv.active = s.a; cv.canvasW = s.cw; cv.canvasH = s.ch;
        afterRestore();
    }

    void redo() {
        if (redoS.isEmpty()) return;
        undoS.add(new Snap(cv.layers, cv.active, cv.canvasW, cv.canvasH));
        Snap s = redoS.remove(redoS.size() - 1);
        cv.layers = s.l; cv.active = s.a; cv.canvasW = s.cw; cv.canvasH = s.ch;
        afterRestore();
    }

    void afterRestore() {
        cv.invalidate();
        refreshEdit();
        refreshLayers();
        updateUndo();
        updateRatioUI();
    }

    void updateUndo() {
        bUndo.setAlpha(undoS.isEmpty() ? 0.35f : 1f);
        bRedo.setAlpha(redoS.isEmpty() ? 0.35f : 1f);
    }

    // ---------------------------------------------------------------- presets & shapes
    LinearLayout tile(View thumb, String caption, View.OnClickListener click) {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(WRAP, WRAP);
        wl.setMargins(dp(5), 0, dp(5), 0);
        w.setLayoutParams(wl);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView t = new TextView(this);
        t.setText(caption);
        t.setTextSize(10);
        t.setTextColor(MUTED);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(4), 0, 0);
        w.addView(thumb);
        w.addView(t);
        w.setOnClickListener(click);
        return w;
    }

    void buildPresets() {
        for (Presets.P p : Presets.ALL) {
            presetRow.addView(tile(new PresetThumb(this, p), p.name, v -> {
                Layer l = cur();
                if (l == null) return;
                push();
                Presets.apply(l, p);
                cv.invalidate();
                refreshEdit();
            }));
        }
    }

    void buildShapes() {
        for (Shapes.S s : Shapes.ALL) {
            shapeRow.addView(tile(new PresetThumb(this, s), s.name, v -> {
                Layer l = cur();
                if (l == null) return;
                push();
                Shapes.apply(l, s, cv.aw(), cv.ah());
                cv.invalidate();
                refreshEdit();
            }));
        }
    }

    // ---------------------------------------------------------------- colour panel
    int getTargetColor() {
        Layer l = cur();
        if (l == null) return 0;
        return target == 0 ? l.c1 : target == 1 ? l.c2 : l.outlineColor;
    }

    void setTargetColor(int c) {
        Layer l = cur();
        if (l == null) return;
        if (target == 0) l.c1 = c; else if (target == 1) l.c2 = c; else l.outlineColor = c;
        cv.invalidate();
    }

    void buildColorPanel() {
        addHandle(colorBox);

        LinearLayout pr = new LinearLayout(this);
        String[] names = {"Start", "End", "Edge"};
        for (int i = 0; i < 3; i++) {
            final int ti = i;
            LinearLayout pill = new LinearLayout(this);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(8), dp(9), dp(8), dp(9));
            View dot = new View(this);
            GradientDrawable dd = new GradientDrawable();
            dd.setShape(GradientDrawable.OVAL);
            dd.setStroke(dp(1), 0x33000000);
            dot.setBackground(dd);
            dot.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView lb = new TextView(this);
            lb.setText(names[i]);
            lb.setTextSize(12);
            lb.setTextColor(DARK);
            lb.setPadding(dp(6), 0, 0, 0);
            pill.addView(dot);
            pill.addView(lb);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            pill.setLayoutParams(lp);
            pill.setOnClickListener(v -> { target = ti; syncColorUI(); });
            dotDraw[i] = dd;
            pills[i] = pill;
            pr.addView(pill);
        }
        colorBox.addView(pr);

        LinearLayout r2 = new LinearLayout(this);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.setPadding(0, dp(10), 0, dp(2));
        previewBar = new View(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        plp.setMargins(dp(3), 0, dp(6), 0);
        previewBar.setLayoutParams(plp);
        r2.addView(previewBar);
        r2.addView(chip("Solid", v -> {
            Layer l = cur(); if (l == null) return;
            push(); l.c2 = l.c1; cv.invalidate(); syncColorUI();
        }));
        r2.addView(chip("Swap", v -> {
            Layer l = cur(); if (l == null) return;
            push(); int t = l.c1; l.c1 = l.c2; l.c2 = t; cv.invalidate(); syncColorUI();
        }));
        colorBox.addView(r2);

        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.CENTER_VERTICAL);
        TextView hl = header(hr, "PALETTE");
        hl.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
        hexText = new TextView(this);
        hexText.setTextSize(11);
        hexText.setTextColor(MUTED);
        hr.addView(hexText);
        colorBox.addView(hr);

        for (int rr = 0; rr < 2; rr++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(4), 0, dp(2));
            for (int k = 0; k < 8; k++) {
                final int col = PAL[rr * 8 + k];
                View sw = new View(this);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(28), dp(28));
                sl.setMargins(dp(4), dp(2), dp(4), dp(2));
                sw.setLayoutParams(sl);
                sw.setBackground(circle(col, false));
                sw.setOnClickListener(v -> {
                    push();
                    setTargetColor(col);
                    pop(v);
                    syncColorUI();
                });
                row.addView(sw);
            }
            colorBox.addView(row);
        }

        header(colorBox, "FINE TUNE");
        int[] hues = new int[7];
        for (int k = 0; k < 7; k++) hues[k] = Color.HSVToColor(new float[]{k * 60f, 1f, 1f});
        GradientDrawable gHue = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, hues);
        gSat = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFFFFF, 0xFF0F9D75});
        gVal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF000000, 0xFF0F9D75});
        sbHue = colorSeek("Hue", 360, gHue);
        sbSat = colorSeek("Sat", 100, gSat);
        sbVal = colorSeek("Light", 100, gVal);
    }

    SeekBar colorSeek(String label, int max, GradientDrawable g) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, 0);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(DARK);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(46), WRAP));
        g.setCornerRadius(dp(4));
        g.setSize(-1, dp(8));
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgressDrawable(g);
        sb.setThumb(getDrawable(R.drawable.seek_thumb));
        sb.setPadding(dp(12), 0, dp(12), 0);
        sb.setSplitTrack(false);
        sb.setLayoutParams(new LinearLayout.LayoutParams(0, dp(30), 1f));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) onColorSliders();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { push(); }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        r.addView(t);
        r.addView(sb);
        colorBox.addView(r);
        return sb;
    }

    void onColorSliders() {
        float h = sbHue.getProgress(), s = sbSat.getProgress() / 100f, v = sbVal.getProgress() / 100f;
        setTargetColor(Color.HSVToColor(new float[]{h, s, v}));
        updateColorVisuals();
    }

    void syncColorUI() {
        if (sbHue == null) return;
        Layer l = cur();
        if (l == null) return;
        float[] hsv = new float[3];
        Color.colorToHSV(getTargetColor(), hsv);
        sbHue.setProgress(Math.round(hsv[0]));
        sbSat.setProgress(Math.round(hsv[1] * 100f));
        sbVal.setProgress(Math.round(hsv[2] * 100f));
        updateColorVisuals();
    }

    void updateColorVisuals() {
        Layer l = cur();
        if (l == null || sbHue == null) return;
        float h = sbHue.getProgress(), s = sbSat.getProgress() / 100f, v = sbVal.getProgress() / 100f;
        gSat.setColors(new int[]{Color.HSVToColor(new float[]{h, 0f, v}), Color.HSVToColor(new float[]{h, 1f, v})});
        gVal.setColors(new int[]{0xFF000000, Color.HSVToColor(new float[]{h, s, 1f})});
        sbSat.invalidate();
        sbVal.invalidate();
        dotDraw[0].setColor(l.c1);
        dotDraw[1].setColor(l.c2);
        dotDraw[2].setColor(l.outlineColor);
        for (int i = 0; i < 3; i++)
            pills[i].setBackgroundResource(i == target ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{l.c1, l.c2});
        pg.setCornerRadius(dp(17));
        if (l.outline > 0.01f) pg.setStroke(dp(3), l.outlineColor); else pg.setStroke(dp(1), 0x33000000);
        previewBar.setBackground(pg);
        hexText.setText(String.format(Locale.US, "#%06X", getTargetColor() & 0xFFFFFF));
    }

    // ---------------------------------------------------------------- sliders (edit + fx panels)
    void addSlider(LinearLayout parent, String label, Getter g, Setter s) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(DARK);
        t.setTextSize(12);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(58), WRAP));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgressDrawable(getDrawable(R.drawable.seek_track));
        sb.setThumb(getDrawable(R.drawable.seek_thumb));
        sb.setPadding(dp(12), 0, dp(12), 0);
        sb.setSplitTrack(false);
        sb.setLayoutParams(new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView val = new TextView(this);
        val.setTextSize(11);
        val.setTextColor(MUTED);
        val.setGravity(Gravity.END);
        val.setLayoutParams(new LinearLayout.LayoutParams(dp(30), WRAP));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar x, int p, boolean user) {
                val.setText(String.valueOf(p));
                if (!user) return;
                Layer l = cur();
                if (l == null) return;
                s.s(l, p / 100f);
                cv.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar x) { push(); }
            @Override public void onStopTrackingTouch(SeekBar x) { }
        });
        r.addView(t);
        r.addView(sb);
        r.addView(val);
        parent.addView(r);
        Row row = new Row();
        row.sb = sb; row.g = g; row.val = val;
        rows.add(row);
    }

    void buildEditPanel() {
        addHandle(editBox);
        header(editBox, "SHAPE");
        addSlider(editBox, "Width", l -> (l.width - 0.005f) / 0.195f, (l, v) -> l.width = 0.005f + v * 0.195f);
        addSlider(editBox, "Smooth", l -> l.smooth, (l, v) -> l.smooth = v);
        addSlider(editBox, "Taper", l -> l.taper, (l, v) -> l.taper = Math.min(v, 0.95f));
        header(editBox, "STYLE");
        addSlider(editBox, "Opacity", l -> l.opacity, (l, v) -> l.opacity = Math.max(0.05f, v));
        addSlider(editBox, "Dash", l -> l.dash, (l, v) -> l.dash = v);
        header(editBox, "EFFECTS");
        addSlider(editBox, "Glow", l -> l.glow, (l, v) -> l.glow = v);
        addSlider(editBox, "Outline", l -> l.outline, (l, v) -> l.outline = v);

        LinearLayout chips = new LinearLayout(this);
        chipRound = chip("Flat ends", v -> {
            Layer l = cur(); if (l == null) return;
            push(); l.round = !l.round; cv.invalidate(); refreshEdit(); pop(v);
        });
        chips.addView(chipRound);
        chips.addView(chip("Random", v -> {
            Layer l = cur(); if (l == null) return;
            push(); l.randomize(rnd, cv.aw(), cv.ah()); cv.invalidate();
        }));
        chips.addView(chip("No mask", v -> clearMask()));
        chips.setPadding(0, dp(10), 0, 0);
        editBox.addView(chips);
    }

    void refreshEdit() {
        Layer l = cur();
        if (l == null) return;
        for (Row r : rows) r.sb.setProgress(Math.round(Math.max(0f, Math.min(1f, r.g.g(l))) * 100));
        if (chipRound != null) {
            chipRound.setText(l.round ? "Round ends" : "Flat ends");
            chipRound.setBackgroundResource(l.round ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
        }
        highlight(bMask, l.mask != null);
        syncColorUI();
    }

    // ---------------------------------------------------------------- shadow (FX) panel
    void buildFxPanel() {
        addHandle(pFx);
        header(pFx, "SHADOW");
        addSlider(pFx, "Shadow", l -> l.shadow, (l, v) -> l.shadow = v);
        addSlider(pFx, "Offset", l -> l.shDist, (l, v) -> l.shDist = v);
        addSlider(pFx, "Blur", l -> l.shBlur, (l, v) -> l.shBlur = v);
        header(pFx, "QUICK STYLES");
        LinearLayout chips = new LinearLayout(this);
        String[] n = {"Off", "Soft", "Hard", "Float"};
        float[][] vals = {{0f, .5f, .5f}, {.5f, .4f, .8f}, {.75f, .3f, .1f}, {.45f, 1f, .9f}};
        for (int i = 0; i < 4; i++) {
            final float[] vv = vals[i];
            chips.addView(chip(n[i], v -> {
                Layer l = cur(); if (l == null) return;
                push(); l.shadow = vv[0]; l.shDist = vv[1]; l.shBlur = vv[2];
                cv.invalidate(); refreshEdit(); pop(v);
            }));
        }
        pFx.addView(chips);
    }

    // ---------------------------------------------------------------- transform panel
    void buildTransformPanel() {
        addHandle(pTransform);
        header(pTransform, "TRANSFORM");
        TextView hint = new TextView(this);
        hint.setText("Twin makes a mirrored copy as a new layer");
        hint.setTextSize(11);
        hint.setTextColor(MUTED);
        hint.setPadding(dp(2), 0, 0, dp(6));
        pTransform.addView(hint);
        GridLayout g = new GridLayout(this);
        g.setColumnCount(3);
        String[] names = {"Rotate 90", "Flip H", "Flip V", "Twin H", "Twin V", "Centre",
                "Bigger", "Smaller", "Reverse", "Fit view"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            g.addView(gchip(names[i], v -> { pop(v); transform(idx); }));
        }
        pTransform.addView(g);
    }

    float[] bounds(Layer l) {
        float x0 = 9f, y0 = 9f, x1 = -9f, y1 = -9f;
        for (float[] p : l.pts) {
            x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]);
            x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]);
        }
        return new float[]{x0, y0, x1, y1};
    }

    void transform(int i) {
        if (i == 9) { cv.fitView(); return; }
        Layer l = cur();
        if (l == null) return;
        push();
        float aw = cv.aw(), ah = cv.ah();
        switch (i) {
            case 0: {
                float[] b = bounds(l);
                float mx = (b[0] + b[2]) / 2f, my = (b[1] + b[3]) / 2f;
                for (float[] p : l.pts) { float dx = p[0] - mx, dy = p[1] - my; p[0] = mx - dy; p[1] = my + dx; }
                break;
            }
            case 1:
                for (float[] p : l.pts) p[0] = aw - p[0];
                break;
            case 2:
                for (float[] p : l.pts) p[1] = ah - p[1];
                break;
            case 3:
            case 4: {
                Layer c = l.copy();
                for (float[] p : c.pts) { if (i == 3) p[0] = aw - p[0]; else p[1] = ah - p[1]; }
                cv.layers.add(cv.active + 1, c);
                cv.active = cv.active + 1;
                refreshLayers();
                break;
            }
            case 5: {
                float[] b = bounds(l);
                float dx = aw / 2f - (b[0] + b[2]) / 2f, dy = ah / 2f - (b[1] + b[3]) / 2f;
                for (float[] p : l.pts) { p[0] += dx; p[1] += dy; }
                break;
            }
            case 6:
            case 7: {
                float f = i == 6 ? 1.1f : 0.9f;
                float[] b = bounds(l);
                float mx = (b[0] + b[2]) / 2f, my = (b[1] + b[3]) / 2f;
                for (float[] p : l.pts) { p[0] = mx + (p[0] - mx) * f; p[1] = my + (p[1] - my) * f; }
                break;
            }
            case 8:
                Collections.reverse(l.pts);
                break;
        }
        cv.invalidate();
    }

    // ---------------------------------------------------------------- canvas ratio panel
    EditText numberField() {
        EditText e = new EditText(this);
        e.setBackgroundResource(R.drawable.glass_input);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(DARK);
        e.setGravity(Gravity.CENTER);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setPadding(dp(8), 0, dp(8), 0);
        e.setLayoutParams(new LinearLayout.LayoutParams(0, dp(46), 1f));
        return e;
    }

    int parseInt(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return fallback; }
    }

    void buildRatioPanel() {
        addHandle(pRatio);
        header(pRatio, "CANVAS RATIO");
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(6), 0, dp(6));
        hs.addView(row);
        for (int i = 0; i < R_NAME.length; i++) {
            final int idx = i;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(dp(74), WRAP);
            tl.setMargins(dp(3), 0, dp(3), 0);
            tile.setLayoutParams(tl);
            RatioView rv = new RatioView(this, R_W[i], R_H[i]);
            rv.setLayoutParams(new LinearLayout.LayoutParams(dp(54), dp(50)));
            TextView nm = new TextView(this);
            nm.setText(R_NAME[i]);
            nm.setTextSize(13);
            nm.setTextColor(DARK);
            nm.setTypeface(null, Typeface.BOLD);
            nm.setPadding(0, dp(4), 0, 0);
            TextView sb = new TextView(this);
            sb.setText(R_SUB[i]);
            sb.setTextSize(10);
            sb.setTextColor(MUTED);
            tile.addView(rv);
            tile.addView(nm);
            tile.addView(sb);
            tile.setOnClickListener(v -> {
                pop(v);
                int longSide = Math.max(cv.canvasW, cv.canvasH);
                int w, h;
                if (R_W[idx] >= R_H[idx]) { w = longSide; h = Math.round(longSide * (float) R_H[idx] / R_W[idx]); }
                else { h = longSide; w = Math.round(longSide * (float) R_W[idx] / R_H[idx]); }
                applyCanvas(w, h);
            });
            ratioViews.add(rv);
            ratioTiles.add(tile);
            row.addView(tile);
        }
        pRatio.addView(hs);

        header(pRatio, "CUSTOM SIZE (PX)");
        LinearLayout cr = new LinearLayout(this);
        cr.setGravity(Gravity.CENTER_VERTICAL);
        cr.setPadding(0, dp(4), 0, dp(4));
        edW = numberField();
        edH = numberField();
        TextView x = new TextView(this);
        x.setText("\u00d7");
        x.setTextSize(18);
        x.setTextColor(MUTED);
        x.setPadding(dp(10), 0, dp(10), 0);
        cr.addView(edW);
        cr.addView(x);
        cr.addView(edH);
        pRatio.addView(cr);

        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void afterTextChanged(Editable e) {
                if (ignoreWatch || !lockRatio) return;
                boolean fromW = edW.hasFocus();
                int base = parseInt(fromW ? edW : edH, 0);
                if (base <= 0) return;
                int other = fromW ? Math.round(base * (float) cv.canvasH / cv.canvasW)
                                  : Math.round(base * (float) cv.canvasW / cv.canvasH);
                ignoreWatch = true;
                (fromW ? edH : edW).setText(String.valueOf(Math.max(1, other)));
                ignoreWatch = false;
            }
        };
        edW.addTextChangedListener(tw);
        edH.addTextChangedListener(tw);
        edH.setOnEditorActionListener((tv, actionId, ev) -> { applyFields(); return true; });
        edW.setOnEditorActionListener((tv, actionId, ev) -> { applyFields(); return true; });

        LinearLayout ar = new LinearLayout(this);
        ar.setGravity(Gravity.CENTER_VERTICAL);
        ar.addView(chip("Swap", v -> {
            pop(v);
            applyCanvas(cv.canvasH, cv.canvasW);
        }));
        lockChip = chip("Lock ratio", v -> {
            lockRatio = !lockRatio;
            pop(v);
            lockChip.setBackgroundResource(lockRatio ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
        });
        ar.addView(lockChip);
        TextView apply = new TextView(this);
        apply.setText("Apply");
        apply.setTextColor(0xFFFFFFFF);
        apply.setTextSize(14);
        apply.setTypeface(null, Typeface.BOLD);
        apply.setGravity(Gravity.CENTER);
        apply.setBackgroundResource(R.drawable.btn_primary);
        apply.setClickable(true);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, dp(44), 1f);
        al.setMargins(dp(8), dp(3), dp(3), dp(3));
        apply.setLayoutParams(al);
        apply.setOnClickListener(v -> { pop(v); applyFields(); });
        ar.addView(apply);
        pRatio.addView(ar);

        ratioInfo = new TextView(this);
        ratioInfo.setTextSize(12);
        ratioInfo.setTextColor(MUTED);
        ratioInfo.setPadding(dp(2), dp(8), 0, 0);
        pRatio.addView(ratioInfo);
    }

    void applyFields() {
        int w = parseInt(edW, cv.canvasW), h = parseInt(edH, cv.canvasH);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(edW.getWindowToken(), 0);
        edW.clearFocus();
        edH.clearFocus();
        if (w > 4096 || h > 4096) Toast.makeText(this, "Max 4096 px", Toast.LENGTH_SHORT).show();
        applyCanvas(w, h);
    }

    void applyCanvas(int w, int h) {
        w = Math.max(64, Math.min(4096, w));
        h = Math.max(64, Math.min(4096, h));
        if (w == cv.canvasW && h == cv.canvasH) { updateRatioUI(); return; }
        push();
        cv.setCanvasSize(w, h);
        updateRatioUI();
    }

    int matchRatio() {
        for (int i = 0; i < R_NAME.length; i++) {
            long diff = Math.abs((long) cv.canvasW * R_H[i] - (long) cv.canvasH * R_W[i]);
            if (diff <= Math.max(R_W[i], R_H[i])) return i;
        }
        return -1;
    }

    static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    void updateRatioUI() {
        if (edW == null || ratioInfo == null) return;
        ignoreWatch = true;
        edW.setText(String.valueOf(cv.canvasW));
        edH.setText(String.valueOf(cv.canvasH));
        ignoreWatch = false;
        int mi = matchRatio();
        for (int i = 0; i < ratioTiles.size(); i++) {
            ratioTiles.get(i).setBackgroundResource(i == mi ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
            ratioViews.get(i).setSel(i == mi);
        }
        String label;
        if (mi >= 0) {
            label = R_NAME[mi] + " " + R_SUB[mi];
        } else {
            int g = gcd(cv.canvasW, cv.canvasH);
            int rw = cv.canvasW / g, rh = cv.canvasH / g;
            label = (rw <= 60 && rh <= 60) ? "Custom " + rw + ":" + rh
                    : String.format(Locale.US, "Custom %.2f:1", (float) cv.canvasW / cv.canvasH);
        }
        ratioInfo.setText(cv.canvasW + " \u00d7 " + cv.canvasH + " px  \u00b7  " + label);
    }

    // ---------------------------------------------------------------- background panel
    void buildBackdropPanel() {
        addHandle(pBackdrop);
        header(pBackdrop, "CANVAS BACKGROUND");
        TextView hint = new TextView(this);
        hint.setText("Transparent stays see-through in PNG");
        hint.setTextSize(11);
        hint.setTextColor(MUTED);
        hint.setPadding(dp(2), 0, 0, dp(4));
        pBackdrop.addView(hint);
        for (int rr = 0; rr < 2; rr++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(4), 0, dp(2));
            for (int k = 0; k < 8; k++) {
                final int col = BG[rr * 8 + k];
                View sw;
                if (col == 0) {
                    ImageView iv = new ImageView(this);
                    iv.setImageResource(R.drawable.swatch_none);
                    iv.setPadding(dp(3), dp(3), dp(3), dp(3));
                    sw = iv;
                } else {
                    sw = new View(this);
                }
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(28), dp(28));
                sl.setMargins(dp(4), dp(2), dp(4), dp(2));
                sw.setLayoutParams(sl);
                sw.setTag(col);
                sw.setOnClickListener(v -> {
                    cv.bgColor = col;
                    cv.invalidate();
                    pop(v);
                    updateBackdropSel();
                });
                bgSwatches.add(sw);
                row.addView(sw);
            }
            pBackdrop.addView(row);
        }
        updateBackdropSel();
    }

    void updateBackdropSel() {
        for (View sw : bgSwatches) {
            int col = (Integer) sw.getTag();
            boolean sel = col == cv.bgColor;
            if (col == 0) {
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Color.TRANSPARENT);
                if (sel) g.setStroke(dp(3), GREEN); else g.setStroke(dp(1), 0x33000000);
                sw.setBackground(g);
            } else {
                sw.setBackground(circle(col, sel));
            }
        }
    }

    // ---------------------------------------------------------------- layers
    ImageButton iconBtn(int res, int sizeDp) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(DARK);
        b.setBackgroundResource(R.drawable.btn_ripple);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return b;
    }

    void refreshLayers() {
        layerList.removeAllViews();
        cur();
        for (int i = 0; i < cv.layers.size(); i++) {
            final int idx = i;
            Layer l = cv.layers.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(2), dp(8), dp(2));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, dp(46));
            rlp.setMargins(0, dp(2), 0, dp(2));
            row.setLayoutParams(rlp);
            if (i == cv.active) row.setBackgroundResource(R.drawable.glass_chip_sel);

            ImageButton eye = iconBtn(R.drawable.ic_eye, 38);
            eye.setAlpha(l.visible ? 1f : 0.3f);
            eye.setOnClickListener(v -> {
                push();
                Layer t = cv.layers.get(idx);
                t.visible = !t.visible;
                cv.invalidate();
                refreshLayers();
            });
            TextView name = new TextView(this);
            name.setText("Line " + (i + 1));
            name.setTextColor(DARK);
            name.setTextSize(14);
            name.setPadding(dp(8), 0, 0, 0);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
            row.addView(eye);
            row.addView(name);
            if (l.mask != null) {
                ImageView mi = new ImageView(this);
                mi.setImageResource(R.drawable.ic_mask);
                mi.setColorFilter(GREEN);
                mi.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
                row.addView(mi);
            }
            ImageButton copy = iconBtn(R.drawable.ic_copy, 38);
            copy.setOnClickListener(v -> {
                push();
                Layer c = cv.layers.get(idx).copy();
                for (float[] p : c.pts) p[1] = Math.min(2f, p[1] + 0.05f);
                cv.layers.add(idx + 1, c);
                cv.active = idx + 1;
                cv.invalidate(); refreshLayers(); refreshEdit();
            });
            ImageButton del = iconBtn(R.drawable.ic_delete, 38);
            del.setOnClickListener(v -> {
                if (cv.layers.size() <= 1) return;
                push();
                cv.layers.remove(idx);
                cv.active = Math.min(cv.active, cv.layers.size() - 1);
                cv.invalidate(); refreshLayers(); refreshEdit();
            });
            row.addView(copy);
            row.addView(del);
            row.setOnClickListener(v -> {
                cv.active = idx;
                cv.invalidate(); refreshLayers(); refreshEdit();
            });
            layerList.addView(row);
        }
    }

    void addLayer() {
        push();
        Layer n = new Layer();
        n.randomize(rnd, cv.aw(), cv.ah());
        Presets.apply(n, Presets.ALL[rnd.nextInt(Presets.ALL.length)]);
        cv.layers.add(n);
        cv.active = cv.layers.size() - 1;
        cv.invalidate(); refreshLayers(); refreshEdit();
    }

    // ---------------------------------------------------------------- mask
    void clearMask() {
        Layer l = cur();
        if (l == null || l.mask == null) return;
        push();
        l.mask = null;
        cv.invalidate(); refreshLayers(); refreshEdit();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_MASK && res == RESULT_OK && data != null && data.getData() != null) {
            Bitmap bm = loadBitmap(data.getData());
            Layer l = cur();
            if (bm != null && l != null) {
                push();
                l.mask = bm;
                cv.invalidate(); refreshLayers(); refreshEdit();
            } else {
                Toast.makeText(this, "Could not open image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    Bitmap loadBitmap(Uri u) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(u);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int ss = 1;
            while (o.outWidth / ss > 2048 || o.outHeight / ss > 2048) ss *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = ss;
            in = getContentResolver().openInputStream(u);
            Bitmap b = BitmapFactory.decodeStream(in, null, o2);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- export sheet
    String defaultName() {
        return "Curvo_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    Bitmap renderPreview(int bg) {
        int cw = cv.canvasW, ch = cv.canvasH;
        float k = 320f / Math.max(cw, ch);
        int pw = Math.max(32, Math.round(cw * k)), ph = Math.max(32, Math.round(ch * k));
        Bitmap b = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path clip = new Path();
        clip.addRoundRect(0f, 0f, pw, ph, 18f, 18f, Path.Direction.CW);
        c.clipPath(clip);
        if (bg == 0) {
            c.drawColor(0xFFFFFFFF);
            Paint p = new Paint();
            p.setColor(0xFFE3EDE8);
            float cell = 20f;
            for (int r = 0; r * cell < ph; r++)
                for (int q = 0; q * cell < pw; q++)
                    if (((r + q) & 1) == 0) c.drawRect(q * cell, r * cell, (q + 1) * cell, (r + 1) * cell, p);
        } else {
            c.drawColor(bg);
        }
        float unit = Math.min(pw, ph);
        for (Layer l : cv.layers) Renderer.draw(c, l, unit, cv.aw(), cv.ah(), 0f, 0f, null, pw, ph);
        return b;
    }

    int[] exportDims(int longSide) {
        int cw = cv.canvasW, ch = cv.canvasH;
        if (longSide <= 0) return new int[]{cw, ch};
        float k = (float) longSide / Math.max(cw, ch);
        return new int[]{Math.max(1, Math.round(cw * k)), Math.max(1, Math.round(ch * k))};
    }

    TextView choiceChip(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(DARK);
        v.setTextSize(13);
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(13), dp(10), dp(13), dp(10));
        v.setBackgroundResource(R.drawable.glass_chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMarginEnd(dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    void showExport() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final View root = getLayoutInflater().inflate(R.layout.dialog_export, null);
        d.setContentView(root);
        d.setCanceledOnTouchOutside(true);
        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setLayout(MATCH, WRAP);
            win.setGravity(Gravity.BOTTOM);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(0.45f);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        final ImageView prev = root.findViewById(R.id.exPreview);
        final TextView info = root.findViewById(R.id.exInfo);
        final TextView bgInfo = root.findViewById(R.id.exBg);
        final TextView ext = root.findViewById(R.id.exExt);
        final TextView save = root.findViewById(R.id.exSave);
        final TextView share = root.findViewById(R.id.exShare);
        final EditText name = root.findViewById(R.id.exName);
        LinearLayout fRow = root.findViewById(R.id.exFormatRow);
        LinearLayout sRow = root.findViewById(R.id.exSizeRow);
        name.setText(defaultName());

        final String[] fNames = {"PNG", "JPG"};
        final int[] sizes = {0, 1080, 2048, 4096};
        final String[] sLabels = {"Canvas", "1080", "2048", "4096"};
        final int[] fmt = {0};
        final int[] sz = {0};
        final TextView[] fChips = new TextView[2];
        final TextView[] sChips = new TextView[4];

        final Runnable refresh = () -> {
            for (int i = 0; i < 2; i++)
                fChips[i].setBackgroundResource(i == fmt[0] ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
            for (int i = 0; i < 4; i++)
                sChips[i].setBackgroundResource(i == sz[0] ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
            boolean jpg = fmt[0] == 1;
            int eff = (jpg && cv.bgColor == 0) ? 0xFFFFFFFF : cv.bgColor;
            int[] dm = exportDims(sizes[sz[0]]);
            info.setText(fNames[fmt[0]] + " \u00b7 " + dm[0] + " \u00d7 " + dm[1] + " px");
            ext.setText(jpg ? ".jpg" : ".png");
            bgInfo.setText(eff == 0 ? "Transparent background" : "Solid background");
            prev.setImageBitmap(renderPreview(eff));
        };

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            TextView c = choiceChip(fNames[i]);
            c.setOnClickListener(v -> { fmt[0] = idx; pop(v); refresh.run(); });
            fChips[i] = c;
            fRow.addView(c);
        }
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView c = choiceChip(sLabels[i]);
            c.setOnClickListener(v -> { sz[0] = idx; pop(v); refresh.run(); });
            sChips[i] = c;
            sRow.addView(c);
        }
        refresh.run();

        save.setOnClickListener(v -> {
            int[] dm = exportDims(sizes[sz[0]]);
            exportNow(d, root, save, share, save, false, fmt[0] == 1, dm[0], dm[1], name.getText().toString());
        });
        share.setOnClickListener(v -> {
            int[] dm = exportDims(sizes[sz[0]]);
            exportNow(d, root, save, share, share, true, fmt[0] == 1, dm[0], dm[1], name.getText().toString());
        });

        // entrance animation: sheet slides up, rows fade in one after another
        root.setTranslationY(dp(520));
        root.setAlpha(0f);
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 1; i < vg.getChildCount(); i++) {
                View ch = vg.getChildAt(i);
                ch.setAlpha(0f);
                ch.setTranslationY(dp(22));
                ch.animate().alpha(1f).translationY(0f).setStartDelay(140 + i * 55L)
                        .setDuration(300).setInterpolator(new DecelerateInterpolator(1.5f)).start();
            }
        }
        d.show();
        root.post(() -> root.animate().translationY(0f).alpha(1f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator(2f)).start());
    }

    void dismissExport(final Dialog d, View root) {
        root.animate().translationY(root.getHeight()).alpha(0f).setDuration(220)
                .withEndAction(() -> { if (d.isShowing()) d.dismiss(); }).start();
    }

    void exportNow(final Dialog d, final View root, final TextView save, final TextView share,
                   final TextView btn, final boolean doShare, final boolean jpg,
                   final int ew, final int eh, String rawName) {
        if (exporting) return;
        exporting = true;
        String n = rawName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (n.isEmpty()) n = defaultName();
        final String fileName = n;
        final CharSequence old = btn.getText();
        btn.setText("Saving...");
        save.setAlpha(0.7f);
        share.setAlpha(0.7f);
        final int eff = (jpg && cv.bgColor == 0) ? 0xFFFFFFFF : cv.bgColor;
        final ArrayList<Layer> snap = cloneAll(cv.layers);
        final float aw = cv.aw(), ah = cv.ah();
        new Thread(() -> {
            Uri u = null;
            boolean oom = false;
            try {
                Bitmap b = Renderer.export(snap, ew, eh, eff, aw, ah);
                u = saveBitmap(b, fileName, jpg);
                b.recycle();
            } catch (OutOfMemoryError e) {
                oom = true;
            } catch (Exception e) {
                u = null;
            }
            final Uri res = u;
            final boolean noMem = oom;
            runOnUiThread(() -> {
                exporting = false;
                save.setAlpha(1f);
                share.setAlpha(1f);
                if (res != null) {
                    btn.setText("Saved \u2713");
                    pop(btn);
                    btn.postDelayed(() -> {
                        dismissExport(d, root);
                        if (doShare) shareUri(res, jpg);
                    }, 700);
                } else {
                    btn.setText(old);
                    Toast.makeText(this, noMem ? "Not enough memory - try a smaller size" : "Save failed",
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    void shareUri(Uri u, boolean jpg) {
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType(jpg ? "image/jpeg" : "image/png");
        s.putExtra(Intent.EXTRA_STREAM, u);
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s, "Share"));
    }

    Uri saveBitmap(Bitmap b, String name, boolean jpg) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name + (jpg ? ".jpg" : ".png"));
            v.put(MediaStore.Images.Media.MIME_TYPE, jpg ? "image/jpeg" : "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Curvo");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            ContentResolver r = getContentResolver();
            Uri u = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            OutputStream os = r.openOutputStream(u);
            b.compress(jpg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            r.update(u, v, null, null);
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- auto-save (masks are not stored)
    void saveSession() {
        try {
            JSONArray arr = new JSONArray();
            for (Layer l : cv.layers) {
                JSONObject o = new JSONObject();
                o.put("v", l.visible);
                JSONArray pa = new JSONArray();
                for (float[] p : l.pts) { pa.put((double) p[0]); pa.put((double) p[1]); }
                o.put("p", pa);
                o.put("c1", l.c1); o.put("c2", l.c2); o.put("oc", l.outlineColor);
                o.put("w", (double) l.width); o.put("op", (double) l.opacity);
                o.put("sm", (double) l.smooth); o.put("tp", (double) l.taper);
                o.put("d", (double) l.dash); o.put("g", (double) l.glow);
                o.put("ol", (double) l.outline); o.put("r", l.round);
                o.put("sh", (double) l.shadow); o.put("sd", (double) l.shDist); o.put("sb", (double) l.shBlur);
                arr.put(o);
            }
            getSharedPreferences("curvo", 0).edit()
                    .putString("s", arr.toString()).putInt("a", cv.active)
                    .putInt("bg", cv.bgColor).putInt("cw", cv.canvasW).putInt("ch", cv.canvasH).apply();
        } catch (Exception ignored) { }
    }

    void loadSession() {
        try {
            cv.bgColor = getSharedPreferences("curvo", 0).getInt("bg", 0);
            cv.canvasW = getSharedPreferences("curvo", 0).getInt("cw", 2048);
            cv.canvasH = getSharedPreferences("curvo", 0).getInt("ch", 2048);
            String s = getSharedPreferences("curvo", 0).getString("s", null);
            if (s == null) return;
            JSONArray arr = new JSONArray(s);
            ArrayList<Layer> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Layer l = new Layer();
                l.visible = o.getBoolean("v");
                JSONArray pa = o.getJSONArray("p");
                for (int k = 0; k + 1 < pa.length(); k += 2)
                    l.pts.add(new float[]{(float) pa.getDouble(k), (float) pa.getDouble(k + 1)});
                l.c1 = o.getInt("c1"); l.c2 = o.getInt("c2"); l.outlineColor = o.getInt("oc");
                l.width = (float) o.getDouble("w"); l.opacity = (float) o.getDouble("op");
                l.smooth = (float) o.getDouble("sm"); l.taper = (float) o.getDouble("tp");
                l.dash = (float) o.getDouble("d"); l.glow = (float) o.getDouble("g");
                l.outline = (float) o.getDouble("ol"); l.round = o.getBoolean("r");
                l.shadow = (float) o.optDouble("sh", 0.0);
                l.shDist = (float) o.optDouble("sd", 0.5);
                l.shBlur = (float) o.optDouble("sb", 0.5);
                if (l.pts.size() >= 2) out.add(l);
            }
            if (!out.isEmpty()) {
                cv.layers = out;
                cv.active = Math.min(getSharedPreferences("curvo", 0).getInt("a", 0), out.size() - 1);
            }
        } catch (Exception ignored) { }
    }
}
