package com.tanzirdev.curvo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.MotionEvent;
import android.util.Base64;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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
    // v1.0.4 state
    ImageButton bDraw, bTrace, bSym, bPattern, bProjects;
    LinearLayout pDraw, pTrace, pSym, pPattern;
    int drawModeSel = 0, traceDetail = 1, traceLines = 0;
    boolean pressureOn = false;
    float drawSmooth = 0.4f;
    final TextView[] drawChips = new TextView[3], detailChips = new TextView[3], lineChips = new TextView[3];
    final TextView[] symChips = new TextView[5], patChips = new TextView[6], gmChips = new TextView[3], blendChips = new TextView[6];
    TextView pressureChip;
    SeekBar sbPoint;
    TextView valPoint;
    boolean pointHint = false;
    String projId = null, projName = "Untitled";
    static final int REQ_TRACE = 12, REQ_OPEN = 13;
    static final String[] EXTS = {".png", ".jpg", ".svg", ".gif", ".mp4"};
    interface OutWriter { void write(OutputStream os) throws Exception; }
    interface FloatCb { void on(float v); }

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
        bDraw = findViewById(R.id.bDraw); bTrace = findViewById(R.id.bTrace);
        bSym = findViewById(R.id.bSym); bPattern = findViewById(R.id.bPattern);
        bProjects = findViewById(R.id.bProjects);
        pDraw = findViewById(R.id.pDraw); pTrace = findViewById(R.id.pTrace);
        pSym = findViewById(R.id.pSym); pPattern = findViewById(R.id.pPattern);

        ImageButton[] all = {bUndo, bRedo, bLayers, bExport, bVector, bPreset, bColor, bEdit, bMask,
                bShapes, bTransform, bFx, bBackdrop, bRatio, bGrid, bAddLayer,
                bDraw, bTrace, bSym, bPattern, bProjects};
        for (ImageButton ib : all) ib.setColorFilter(DARK);

        installCrashReporter();
        loadSession();
        if (cv.layers.isEmpty()) { cv.layers.add(Layer.defaultLayer()); cv.active = 0; }

        cv.listener = new CurveView.Listener() {
            @Override public void onBeforeChange() { push(); }
            @Override public void onChanged() { }
            @Override public void onMessage(String m) {
                Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
            }
            @Override public void onStroke(ArrayList<float[]> pts) { handleStroke(pts); }
            @Override public void onSelect(int idx) { updatePointSlider(idx); }
        };

        buildPresets();
        buildShapes();
        buildColorPanel();
        buildEditPanel();
        buildTransformPanel();
        buildBackdropPanel();
        buildFxPanel();
        buildRatioPanel();
        buildDrawPanel();
        buildTracePanel();
        buildSymPanel();
        buildPatternPanel();
        buildLayerExtras();
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
        bDraw.setOnClickListener(v -> showPanel(9));
        bTrace.setOnClickListener(v -> showPanel(10));
        bSym.setOnClickListener(v -> showPanel(11));
        bPattern.setOnClickListener(v -> showPanel(12));
        bProjects.setOnClickListener(v -> showProjects());
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
        if (projId != null) saveProject(projName, false, true);
    }

    // ---------------------------------------------------------------- crash reporter
    void installCrashReporter() {
        final File f = new File(getFilesDir(), "crash.txt");
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                FileOutputStream o = new FileOutputStream(f);
                o.write(sw.toString().getBytes("UTF-8"));
                o.close();
            } catch (Throwable ignored) { }
            if (old != null) old.uncaughtException(t, e);
        });
        if (f.exists()) {
            try {
                FileInputStream in = new FileInputStream(f);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                f.delete();
                final String trace = bo.toString("UTF-8");
                getWindow().getDecorView().post(() -> new AlertDialog.Builder(this)
                        .setTitle("Curvo closed unexpectedly last time")
                        .setMessage(trace.length() > 1400 ? trace.substring(0, 1400) + "\n..." : trace)
                        .setPositiveButton("Copy error", (dl, w) -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Curvo error", trace));
                            Toast.makeText(this, "Copied. Paste it in the chat.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Close", null).show());
            } catch (Throwable ignored) { }
        }
    }

    // ---------------------------------------------------------------- helpers
    Layer curEd() {
        Layer l = cur();
        if (l != null && l.locked) {
            Toast.makeText(this, "Layer is locked", Toast.LENGTH_SHORT).show();
            return null;
        }
        return l;
    }

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
        View[] ps = {pPreset, pColor, pEdit, pLayers, pShapes, pTransform, pBackdrop, pFx, pRatio, pDraw, pTrace, pSym, pPattern};
        ImageButton[] bs = {bPreset, bColor, bEdit, bLayers, bShapes, bTransform, bBackdrop, bFx, bRatio, bDraw, bTrace, bSym, bPattern};
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
            if (id == 11) updateSymUI();
            if (id == 12) updatePatUI();
        }
        cv.drawMode = (curPanel == 9);
        cv.invalidate();
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
                Layer l = curEd();
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
                Layer l = curEd();
                if (l == null) return;
                push();
                Shapes.apply(l, s, cv.aw(), cv.ah());
                cv.invalidate();
                refreshEdit();
            }));
        }
    }

    // ---------------------------------------------------------------- colour panel
    // targets: 0 start, 1 end, 2 edge, 3 pattern, 4.. middle gradient stops
    final ArrayList<Integer> pillTargets = new ArrayList<>();
    final ArrayList<GradientDrawable> pillDots = new ArrayList<>();
    final ArrayList<LinearLayout> pillViews = new ArrayList<>();
    LinearLayout pillRow;

    int colorOf(Layer l, int t) {
        switch (t) {
            case 0: return l.c1;
            case 1: return l.c2;
            case 2: return l.outlineColor;
            case 3: return l.patColor;
            default: {
                int i = t - 4;
                return (i >= 0 && i < l.mid.size()) ? l.mid.get(i) : l.c1;
            }
        }
    }

    String targetName(int t) {
        switch (t) {
            case 0: return "Start";
            case 1: return "End";
            case 2: return "Edge";
            case 3: return "Pattern";
            default: return "Mid " + (t - 3);
        }
    }

    int getTargetColor() {
        Layer l = cur();
        return l == null ? 0 : colorOf(l, target);
    }

    void setTargetColor(int c) {
        Layer l = curEd();
        if (l == null) return;
        switch (target) {
            case 0: l.c1 = c; break;
            case 1: l.c2 = c; break;
            case 2: l.outlineColor = c; break;
            case 3: l.patColor = c; break;
            default: {
                int i = target - 4;
                if (i >= 0 && i < l.mid.size()) l.mid.set(i, c);
            }
        }
        cv.invalidate();
    }

    static int mixColor(int a, int b) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | (((ar + br) / 2) << 16) | (((ag + bg) / 2) << 8) | ((ab + bb) / 2);
    }

    void rebuildPills() {
        if (pillRow == null) return;
        Layer l = cur();
        if (l == null) return;
        pillRow.removeAllViews();
        pillTargets.clear();
        pillDots.clear();
        pillViews.clear();
        ArrayList<Integer> order = new ArrayList<>();
        order.add(0);
        for (int i = 0; i < l.mid.size(); i++) order.add(4 + i);
        order.add(1);
        order.add(2);
        order.add(3);
        if (!order.contains(target)) target = 0;
        for (int t : order) {
            final int ti = t;
            LinearLayout pill = new LinearLayout(this);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(12), dp(9), dp(12), dp(9));
            View dot = new View(this);
            GradientDrawable dd = new GradientDrawable();
            dd.setShape(GradientDrawable.OVAL);
            dd.setStroke(dp(1), 0x33000000);
            dot.setBackground(dd);
            dot.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView lb = new TextView(this);
            lb.setText(targetName(t));
            lb.setTextSize(12);
            lb.setTextColor(DARK);
            lb.setPadding(dp(6), 0, 0, 0);
            pill.addView(dot);
            pill.addView(lb);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.setMargins(dp(3), 0, dp(3), 0);
            pill.setLayoutParams(lp);
            pill.setOnClickListener(v -> { target = ti; syncColorUI(); });
            pillTargets.add(t);
            pillDots.add(dd);
            pillViews.add(pill);
            pillRow.addView(pill);
        }
    }

    void buildColorPanel() {
        addHandle(colorBox);

        HorizontalScrollView ph = new HorizontalScrollView(this);
        ph.setHorizontalScrollBarEnabled(false);
        pillRow = new LinearLayout(this);
        ph.addView(pillRow);
        colorBox.addView(ph);

        LinearLayout r2 = new LinearLayout(this);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.setPadding(0, dp(10), 0, dp(2));
        previewBar = new View(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        plp.setMargins(dp(3), 0, dp(6), 0);
        previewBar.setLayoutParams(plp);
        r2.addView(previewBar);
        r2.addView(chip("Solid", v -> {
            Layer l = curEd(); if (l == null) return;
            push(); l.c2 = l.c1; l.mid.clear(); cv.invalidate(); syncColorUI();
        }));
        r2.addView(chip("Swap", v -> {
            Layer l = curEd(); if (l == null) return;
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
                    Layer l = curEd(); if (l == null) return;
                    push();
                    setTargetColor(col);
                    pop(v);
                    syncColorUI();
                });
                row.addView(sw);
            }
            colorBox.addView(row);
        }

        header(colorBox, "GRADIENT");
        LinearLayout gr = new LinearLayout(this);
        gr.addView(chip("+ Stop", v -> {
            Layer l = curEd(); if (l == null) return;
            if (l.mid.size() >= 3) { Toast.makeText(this, "Max 5 colours", Toast.LENGTH_SHORT).show(); return; }
            push();
            int last = l.mid.isEmpty() ? l.c1 : l.mid.get(l.mid.size() - 1);
            l.mid.add(mixColor(last, l.c2));
            target = 4 + l.mid.size() - 1;
            cv.invalidate(); syncColorUI();
        }));
        gr.addView(chip("- Stop", v -> {
            Layer l = curEd(); if (l == null) return;
            if (l.mid.isEmpty()) return;
            push();
            l.mid.remove(l.mid.size() - 1);
            if (target >= 4 + l.mid.size() && target >= 4) target = 0;
            cv.invalidate(); syncColorUI();
        }));
        String[] gm = {"Line", "Angle", "Radial"};
        for (int i = 0; i < 3; i++) {
            final int gi = i;
            gmChips[i] = chip(gm[i], v -> {
                Layer l = curEd(); if (l == null) return;
                push(); l.gradMode = gi; cv.invalidate(); syncColorUI(); pop(v);
            });
            gr.addView(gmChips[i]);
        }
        HorizontalScrollView gh = new HorizontalScrollView(this);
        gh.setHorizontalScrollBarEnabled(false);
        gh.addView(gr);
        colorBox.addView(gh);
        addSlider(colorBox, "Angle", l -> l.gradAngle / 360f, (l, v) -> l.gradAngle = v * 360f);

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
            @Override public void onStartTrackingTouch(SeekBar s) {
                Layer l = cur();
                if (l != null && !l.locked) push();
            }
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
        rebuildPills();
        float[] hsv = new float[3];
        Color.colorToHSV(getTargetColor(), hsv);
        sbHue.setProgress(Math.round(hsv[0]));
        sbSat.setProgress(Math.round(hsv[1] * 100f));
        sbVal.setProgress(Math.round(hsv[2] * 100f));
        updateColorVisuals();
    }

    void updateColorVisuals() {
        Layer l = cur();
        if (l == null || sbHue == null || previewBar == null) return;
        float h = sbHue.getProgress(), s = sbSat.getProgress() / 100f, v = sbVal.getProgress() / 100f;
        gSat.setColors(new int[]{Color.HSVToColor(new float[]{h, 0f, v}), Color.HSVToColor(new float[]{h, 1f, v})});
        gVal.setColors(new int[]{0xFF000000, Color.HSVToColor(new float[]{h, s, 1f})});
        sbSat.invalidate();
        sbVal.invalidate();
        for (int i = 0; i < pillTargets.size(); i++) {
            int t = pillTargets.get(i);
            pillDots.get(i).setColor(colorOf(l, t));
            pillViews.get(i).setBackgroundResource(t == target ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
        }
        for (int i = 0; i < 3; i++)
            if (gmChips[i] != null) gmChips[i].setBackgroundResource(i == l.gradMode ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, l.colors());
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
                Layer l = curEd();
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
        header(editBox, "BRUSH (POINT WIDTH)");
        TextView bhint = new TextView(this);
        bhint.setText("Select a point in Vector mode, then change its width");
        bhint.setTextSize(11);
        bhint.setTextColor(MUTED);
        bhint.setPadding(dp(2), 0, 0, dp(4));
        editBox.addView(bhint);
        addPointWidthRow();
        LinearLayout bchips = new LinearLayout(this);
        String[] bn = {"Even", "Taper", "Swell", "Pulse", "Random"};
        for (int i = 0; i < bn.length; i++) {
            final int bi = i;
            bchips.addView(chip(bn[i], v -> { pop(v); widthPreset(bi); }));
        }
        HorizontalScrollView bhs = new HorizontalScrollView(this);
        bhs.setHorizontalScrollBarEnabled(false);
        bhs.addView(bchips);
        editBox.addView(bhs);
        header(editBox, "STYLE");
        addSlider(editBox, "Opacity", l -> l.opacity, (l, v) -> l.opacity = Math.max(0.05f, v));
        addSlider(editBox, "Dash", l -> l.dash, (l, v) -> l.dash = v);
        header(editBox, "EFFECTS");
        addSlider(editBox, "Glow", l -> l.glow, (l, v) -> l.glow = v);
        addSlider(editBox, "Outline", l -> l.outline, (l, v) -> l.outline = v);

        LinearLayout chips = new LinearLayout(this);
        chipRound = chip("Flat ends", v -> {
            Layer l = curEd(); if (l == null) return;
            push(); l.round = !l.round; cv.invalidate(); refreshEdit(); pop(v);
        });
        chips.addView(chipRound);
        chips.addView(chip("Random", v -> {
            Layer l = curEd(); if (l == null) return;
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
        updatePointSlider(cv.getSelectedIndex());
        updateSymUI();
        updatePatUI();
        updateBlendChips();
    }

    // ---- variable width (per point)
    void addPointWidthRow() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText("Point");
        t.setTextColor(DARK);
        t.setTextSize(12);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(58), WRAP));
        sbPoint = new SeekBar(this);
        sbPoint.setMax(100);
        sbPoint.setProgressDrawable(getDrawable(R.drawable.seek_track));
        sbPoint.setThumb(getDrawable(R.drawable.seek_thumb));
        sbPoint.setPadding(dp(12), 0, dp(12), 0);
        sbPoint.setSplitTrack(false);
        sbPoint.setLayoutParams(new LinearLayout.LayoutParams(0, dp(34), 1f));
        valPoint = new TextView(this);
        valPoint.setTextSize(11);
        valPoint.setTextColor(MUTED);
        valPoint.setGravity(Gravity.END);
        valPoint.setLayoutParams(new LinearLayout.LayoutParams(dp(30), WRAP));
        sbPoint.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar x, int p, boolean user) {
                valPoint.setText(String.valueOf(p));
                if (!user) return;
                Layer l = curEd();
                int si = cv.getSelectedIndex();
                if (l == null) return;
                if (si < 0 || si >= l.pts.size()) {
                    if (!pointHint) { pointHint = true; Toast.makeText(MainActivity.this, "Select a point first (Vector mode)", Toast.LENGTH_SHORT).show(); }
                    return;
                }
                l.pts.get(si)[2] = 0.2f + p / 100f * 2.8f;
                cv.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar x) {
                Layer l = cur();
                int si = cv.getSelectedIndex();
                if (l != null && !l.locked && si >= 0 && si < l.pts.size()) push();
            }
            @Override public void onStopTrackingTouch(SeekBar x) { }
        });
        r.addView(t);
        r.addView(sbPoint);
        r.addView(valPoint);
        editBox.addView(r);
    }

    void updatePointSlider(int idx) {
        if (sbPoint == null) return;
        Layer l = cur();
        boolean ok = l != null && idx >= 0 && idx < l.pts.size();
        sbPoint.setAlpha(ok ? 1f : 0.4f);
        if (ok) {
            float wm = l.pts.get(idx).length > 2 ? l.pts.get(idx)[2] : 1f;
            sbPoint.setProgress(Math.max(0, Math.min(100, Math.round((wm - 0.2f) / 2.8f * 100f))));
        } else {
            sbPoint.setProgress(Math.round((1f - 0.2f) / 2.8f * 100f));
        }
    }

    void widthPreset(int kind) {
        Layer l = curEd();
        if (l == null) return;
        push();
        int n = l.pts.size();
        for (int i = 0; i < n; i++) {
            float t = n > 1 ? i / (float) (n - 1) : 0f;
            float wm;
            switch (kind) {
                case 1: wm = 0.3f + 0.9f * (float) Math.sin(Math.PI * t); break;
                case 2: wm = 0.5f + 1.7f * (float) Math.sin(Math.PI * t); break;
                case 3: wm = (i % 2 == 0) ? 0.6f : 1.6f; break;
                case 4: wm = 0.4f + 1.4f * rnd.nextFloat(); break;
                default: wm = 1f;
            }
            l.pts.get(i)[2] = Math.max(0.2f, Math.min(3f, wm));
        }
        cv.invalidate();
        updatePointSlider(cv.getSelectedIndex());
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
                Layer l = curEd(); if (l == null) return;
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
        Layer l = curEd();
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

    // ---------------------------------------------------------------- layers (smart layers)
    static final String[] BLEND = {"Normal", "Multiply", "Screen", "Overlay", "Darken", "Lighten"};

    ImageButton iconBtn(int res, int sizeDp) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(DARK);
        b.setBackgroundResource(R.drawable.btn_ripple);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return b;
    }

    String layerName(int i) {
        Layer l = cv.layers.get(i);
        return l.name.equals("Line") ? "Line " + (i + 1) : l.name;
    }

    void buildLayerExtras() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(6), 0, 0);
        for (int i = 0; i < BLEND.length; i++) {
            final int bi = i;
            blendChips[i] = chip(BLEND[i], v -> {
                Layer l = curEd(); if (l == null) return;
                push(); l.blend = bi; cv.invalidate(); updateBlendChips(); pop(v);
            });
            row.addView(blendChips[i]);
        }
        hs.addView(row);
        ((LinearLayout) pLayers).addView(hs);
    }

    void updateBlendChips() {
        Layer l = cur();
        if (l == null) return;
        for (int i = 0; i < blendChips.length; i++)
            if (blendChips[i] != null)
                blendChips[i].setBackgroundResource(i == l.blend ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
    }

    void refreshLayers() {
        layerList.removeAllViews();
        cur();
        final int n = cv.layers.size();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final Layer l = cv.layers.get(i);
            final LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(2), dp(6), dp(2));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, dp(46));
            rlp.setMargins(0, dp(2), 0, dp(2));
            row.setLayoutParams(rlp);
            if (i == cv.active) row.setBackgroundResource(R.drawable.glass_chip_sel);

            ImageView drag = new ImageView(this);
            drag.setImageResource(R.drawable.ic_drag);
            drag.setColorFilter(MUTED);
            drag.setPadding(dp(6), dp(8), dp(6), dp(8));
            drag.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(40)));
            drag.setOnTouchListener(new View.OnTouchListener() {
                float startY;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            startY = e.getRawY();
                            if (layerList.getParent() != null) layerList.getParent().requestDisallowInterceptTouchEvent(true);
                            row.setTranslationZ(dp(8));
                            row.setAlpha(0.92f);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            row.setTranslationY(e.getRawY() - startY);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL: {
                            float dy = e.getRawY() - startY;
                            row.setTranslationY(0f);
                            row.setTranslationZ(0f);
                            row.setAlpha(1f);
                            int to = Math.max(0, Math.min(n - 1, idx + Math.round(dy / dp(50))));
                            if (e.getActionMasked() == MotionEvent.ACTION_UP && to != idx) {
                                push();
                                Layer mv = cv.layers.remove(idx);
                                cv.layers.add(to, mv);
                                cv.active = to;
                                cv.invalidate();
                                refreshLayers();
                                refreshEdit();
                            }
                            return true;
                        }
                    }
                    return false;
                }
            });

            ImageButton eye = iconBtn(R.drawable.ic_eye, 36);
            eye.setAlpha(l.visible ? 1f : 0.3f);
            eye.setOnClickListener(v -> {
                push();
                Layer t = cv.layers.get(idx);
                t.visible = !t.visible;
                cv.invalidate();
                refreshLayers();
            });
            TextView name = new TextView(this);
            name.setText(layerName(i));
            name.setTextColor(DARK);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setPadding(dp(6), 0, 0, 0);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
            name.setOnLongClickListener(v -> { renameLayer(idx); return true; });
            row.addView(drag);
            row.addView(eye);
            row.addView(name);
            if (l.mask != null) {
                ImageView mi = new ImageView(this);
                mi.setImageResource(R.drawable.ic_mask);
                mi.setColorFilter(GREEN);
                mi.setLayoutParams(new LinearLayout.LayoutParams(dp(18), dp(18)));
                row.addView(mi);
            }
            ImageButton lock = iconBtn(l.locked ? R.drawable.ic_lock : R.drawable.ic_unlock, 36);
            lock.setColorFilter(l.locked ? GREEN : MUTED);
            lock.setOnClickListener(v -> {
                push();
                Layer t = cv.layers.get(idx);
                t.locked = !t.locked;
                cv.invalidate();
                refreshLayers();
            });
            ImageButton copy = iconBtn(R.drawable.ic_copy, 36);
            copy.setOnClickListener(v -> {
                push();
                Layer c = cv.layers.get(idx).copy();
                c.locked = false;
                for (float[] p : c.pts) p[1] = Math.min(2f, p[1] + 0.05f);
                cv.layers.add(idx + 1, c);
                cv.active = idx + 1;
                cv.invalidate(); refreshLayers(); refreshEdit();
            });
            ImageButton del = iconBtn(R.drawable.ic_delete, 36);
            del.setOnClickListener(v -> {
                if (cv.layers.size() <= 1) return;
                push();
                cv.layers.remove(idx);
                cv.active = Math.min(cv.active, cv.layers.size() - 1);
                cv.invalidate(); refreshLayers(); refreshEdit();
            });
            row.addView(lock);
            row.addView(copy);
            row.addView(del);
            row.setOnClickListener(v -> {
                cv.active = idx;
                cv.invalidate(); refreshLayers(); refreshEdit();
            });
            layerList.addView(row);
        }
        updateBlendChips();
    }

    interface StrCb { void on(String s); }

    void promptName(String title, String initial, final StrCb cb) {
        final EditText et = new EditText(this);
        et.setText(initial);
        et.setSingleLine(true);
        et.setSelectAllOnFocus(true);
        et.setPadding(dp(24), dp(16), dp(24), dp(16));
        new AlertDialog.Builder(this).setTitle(title).setView(et)
                .setPositiveButton("OK", (dlg, w) -> {
                    String t = et.getText().toString().trim();
                    if (!t.isEmpty()) cb.on(t);
                })
                .setNegativeButton("Cancel", null).show();
    }

    void renameLayer(final int i) {
        promptName("Rename layer", layerName(i), t -> {
            if (i < 0 || i >= cv.layers.size()) return;
            push();
            cv.layers.get(i).name = t;
            refreshLayers();
        });
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

    // ---------------------------------------------------------------- freehand panel
    TextView hintText(String t) {
        TextView h = new TextView(this);
        h.setText(t);
        h.setTextSize(11);
        h.setTextColor(MUTED);
        h.setPadding(dp(2), 0, 0, dp(6));
        return h;
    }

    void addPlainSlider(LinearLayout parent, String label, int initial, final FloatCb cb) {
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
        sb.setProgress(initial);
        sb.setLayoutParams(new LinearLayout.LayoutParams(0, dp(34), 1f));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar x, int p, boolean user) { if (user) cb.on(p / 100f); }
            @Override public void onStartTrackingTouch(SeekBar x) { }
            @Override public void onStopTrackingTouch(SeekBar x) { }
        });
        r.addView(t);
        r.addView(sb);
        parent.addView(r);
    }

    void buildDrawPanel() {
        addHandle(pDraw);
        header(pDraw, "FREEHAND");
        pDraw.addView(hintText("Draw on the canvas with one finger. When you lift it, the stroke becomes a smooth line with editable points."));
        LinearLayout mc = new LinearLayout(this);
        String[] mn = {"New layer", "Replace line", "Append"};
        for (int i = 0; i < 3; i++) {
            final int mi = i;
            drawChips[i] = chip(mn[i], v -> { drawModeSel = mi; updateDrawUI(); pop(v); });
            mc.addView(drawChips[i]);
        }
        pDraw.addView(mc);
        LinearLayout pc = new LinearLayout(this);
        pressureChip = chip("Pressure width", v -> { pressureOn = !pressureOn; updateDrawUI(); pop(v); });
        pc.addView(pressureChip);
        pDraw.addView(pc);
        addPlainSlider(pDraw, "Smooth", Math.round(drawSmooth * 100f), v -> drawSmooth = v);
        updateDrawUI();
    }

    void updateDrawUI() {
        for (int i = 0; i < 3; i++)
            if (drawChips[i] != null) drawChips[i].setBackgroundResource(i == drawModeSel ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
        if (pressureChip != null)
            pressureChip.setBackgroundResource(pressureOn ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
    }

    void handleStroke(ArrayList<float[]> raw) {
        if (raw == null || raw.size() < 2) return;
        Layer cl = cur();
        if (cl == null) return;
        if (drawModeSel != 0 && cl.locked) {
            Toast.makeText(this, "Layer is locked", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<float[]> sm = Geom.smooth(raw, 2);
        float eps = 0.004f + drawSmooth * 0.035f;
        ArrayList<float[]> simp = Geom.limit(sm, eps, 60);
        if (simp.size() < 2) {
            simp = new ArrayList<>();
            simp.add(sm.get(0));
            simp.add(sm.get(sm.size() - 1));
        }
        float avgP = 0f;
        for (float[] p : simp) avgP += p[2];
        avgP /= simp.size();
        ArrayList<float[]> np = new ArrayList<>();
        for (float[] p : simp) {
            float wm = 1f;
            if (pressureOn && avgP > 0.01f) wm = Math.max(0.3f, Math.min(2.2f, p[2] / avgP));
            np.add(new float[]{p[0], p[1], wm});
        }
        push();
        if (drawModeSel == 0) {
            Layer n = cl.copy();
            n.name = "Line";
            n.mask = null;
            n.locked = false;
            n.pts = np;
            n.reveal = 1f;
            cv.layers.add(cv.active + 1, n);
            cv.active = cv.active + 1;
        } else if (drawModeSel == 1) {
            cl.pts = np;
        } else {
            cl.pts.addAll(np);
        }
        cv.invalidate();
        refreshLayers();
        refreshEdit();
    }

    // ---------------------------------------------------------------- trace panel
    void buildTracePanel() {
        addHandle(pTrace);
        header(pTrace, "TRACE IMAGE TO VECTOR");
        pTrace.addView(hintText("Pick a PNG or photo with a clear thick line. It becomes editable points, with width and colour."));
        header(pTrace, "DETAIL");
        LinearLayout dc = new LinearLayout(this);
        String[] dn = {"Low", "Medium", "High"};
        for (int i = 0; i < 3; i++) {
            final int di = i;
            detailChips[i] = chip(dn[i], v -> { traceDetail = di; updateTraceUI(); pop(v); });
            dc.addView(detailChips[i]);
        }
        pTrace.addView(dc);
        header(pTrace, "MAX LINES");
        LinearLayout lc = new LinearLayout(this);
        String[] ln = {"1", "3", "6"};
        for (int i = 0; i < 3; i++) {
            final int li = i;
            lineChips[i] = chip(ln[i], v -> { traceLines = li; updateTraceUI(); pop(v); });
            lc.addView(lineChips[i]);
        }
        pTrace.addView(lc);
        TextView go = new TextView(this);
        go.setText("Choose image");
        go.setTextColor(0xFFFFFFFF);
        go.setTextSize(15);
        go.setTypeface(null, Typeface.BOLD);
        go.setGravity(Gravity.CENTER);
        go.setBackgroundResource(R.drawable.btn_primary);
        go.setClickable(true);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(MATCH, dp(48));
        gl.topMargin = dp(14);
        go.setLayoutParams(gl);
        go.setOnClickListener(v -> {
            pop(v);
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(i, REQ_TRACE);
        });
        pTrace.addView(go);
        updateTraceUI();
    }

    void updateTraceUI() {
        for (int i = 0; i < 3; i++) {
            if (detailChips[i] != null) detailChips[i].setBackgroundResource(i == traceDetail ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
            if (lineChips[i] != null) lineChips[i].setBackgroundResource(i == traceLines ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
        }
    }

    void runTrace(final Bitmap bm) {
        final int lines = traceLines == 0 ? 1 : (traceLines == 1 ? 3 : 6);
        final float eps = traceDetail == 0 ? 3.2f : (traceDetail == 1 ? 1.8f : 0.9f);
        final float aw = cv.aw(), ah = cv.ah();
        Toast.makeText(this, "Tracing...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            ArrayList<Tracer.Line> res = null;
            try {
                res = Tracer.trace(bm, lines, eps);
            } catch (Throwable ignored) { }
            final ArrayList<Tracer.Line> fr = res;
            runOnUiThread(() -> applyTrace(fr, aw, ah));
        }).start();
    }

    void applyTrace(ArrayList<Tracer.Line> res, float aw, float ah) {
        if (res == null || res.isEmpty()) {
            Toast.makeText(this, "No clear line found. Try a darker, thicker line.", Toast.LENGTH_LONG).show();
            return;
        }
        push();
        int count = 0;
        for (Tracer.Line ln : res) {
            float sc = Math.min(aw / ln.gw, ah / ln.gh) * 0.95f;
            Layer n = new Layer();
            Presets.apply(n, Presets.ALL[0]);
            n.name = "Trace";
            n.c1 = ln.color;
            n.c2 = ln.color;
            n.width = Math.max(0.004f, Math.min(0.5f, ln.thick * sc));
            n.smooth = 0.5f;
            for (float[] p : ln.pts)
                n.pts.add(new float[]{(p[0] - ln.gw / 2f) * sc + aw / 2f, (p[1] - ln.gh / 2f) * sc + ah / 2f, p[2]});
            cv.layers.add(Math.min(cv.layers.size(), cv.active + 1 + count), n);
            count++;
        }
        cv.active = Math.min(cv.layers.size() - 1, cv.active + count);
        cv.invalidate();
        refreshLayers();
        refreshEdit();
        Toast.makeText(this, "Traced " + count + (count == 1 ? " line" : " lines"), Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- symmetry panel
    void buildSymPanel() {
        addHandle(pSym);
        header(pSym, "SYMMETRY");
        pSym.addView(hintText("Draw on one side and the rest follows live. Bake turns the copies into real layers."));
        GridLayout g = new GridLayout(this);
        g.setColumnCount(3);
        String[] names = {"Off", "Mirror L/R", "Mirror T/B", "Quad (4)", "Radial (6)"};
        for (int i = 0; i < 5; i++) {
            final int si = i;
            symChips[i] = gchip(names[i], v -> {
                Layer l = curEd(); if (l == null) return;
                push(); l.sym = si; cv.invalidate(); updateSymUI(); pop(v);
            });
            g.addView(symChips[i]);
        }
        g.addView(gchip("Bake", v -> { pop(v); bakeSymmetry(); }));
        pSym.addView(g);
        updateSymUI();
    }

    void updateSymUI() {
        Layer l = cur();
        if (l == null) return;
        for (int i = 0; i < symChips.length; i++)
            if (symChips[i] != null) symChips[i].setBackgroundResource(i == l.sym ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
    }

    void bakeSymmetry() {
        Layer l = curEd();
        if (l == null) return;
        ArrayList<Layer> copies = Renderer.symCopies(l, cv.aw(), cv.ah());
        if (copies.size() < 2) {
            Toast.makeText(this, "Turn on a symmetry mode first", Toast.LENGTH_SHORT).show();
            return;
        }
        push();
        int at = cv.layers.indexOf(l);
        for (int k = 1; k < copies.size(); k++) {
            Layer c = copies.get(k);
            c.sym = 0;
            c.locked = false;
            c.reveal = 1f;
            cv.layers.add(at + k, c);
        }
        l.sym = 0;
        cv.invalidate();
        refreshLayers();
        refreshEdit();
        Toast.makeText(this, "Baked " + (copies.size() - 1) + " copies", Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- pattern panel
    void buildPatternPanel() {
        addHandle(pPattern);
        header(pPattern, "PATTERN FILL");
        pPattern.addView(hintText("Road and Rail follow the line. Stripes, Dots and Noise fill the ribbon."));
        GridLayout g = new GridLayout(this);
        g.setColumnCount(3);
        String[] names = {"None", "Stripes", "Dots", "Noise", "Road", "Rail"};
        for (int i = 0; i < 6; i++) {
            final int pi = i;
            patChips[i] = gchip(names[i], v -> {
                Layer l = curEd(); if (l == null) return;
                push();
                l.pat = pi;
                if (pi == 4) { l.patColor = 0xFFFFFFFF; }
                if (pi == 5) { l.patColor = 0xFF3B2F2F; }
                cv.invalidate(); updatePatUI(); pop(v);
            });
            g.addView(patChips[i]);
        }
        pPattern.addView(g);
        addSlider(pPattern, "Scale", l -> l.patScale, (l, v) -> l.patScale = v);
        LinearLayout pc = new LinearLayout(this);
        pc.addView(chip("Pattern colour", v -> {
            pop(v);
            showPanel(1);
            target = 3;
            syncColorUI();
        }));
        pPattern.addView(pc);
        updatePatUI();
    }

    void updatePatUI() {
        Layer l = cur();
        if (l == null) return;
        for (int i = 0; i < patChips.length; i++)
            if (patChips[i] != null) patChips[i].setBackgroundResource(i == l.pat ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
    }

    // ---------------------------------------------------------------- mask
    void clearMask() {
        Layer l = curEd();
        if (l == null || l.mask == null) return;
        push();
        l.mask = null;
        cv.invalidate(); refreshLayers(); refreshEdit();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_MASK) {
            Bitmap bm = loadBitmap(uri);
            Layer l = curEd();
            if (bm != null && l != null) {
                push();
                l.mask = bm;
                cv.invalidate(); refreshLayers(); refreshEdit();
            } else if (bm == null) {
                Toast.makeText(this, "Could not open image", Toast.LENGTH_SHORT).show();
            }
        } else if (req == REQ_TRACE) {
            Bitmap bm = loadBitmap(uri);
            if (bm == null) Toast.makeText(this, "Could not open image", Toast.LENGTH_SHORT).show();
            else runTrace(bm);
        } else if (req == REQ_OPEN) {
            importCurvo(uri);
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

    int[] animDims(boolean mp4, int longSide) {
        int cw = cv.canvasW, ch = cv.canvasH;
        int cap = mp4 ? 1280 : 720;
        int ls = longSide <= 0 ? Math.max(cw, ch) : longSide;
        ls = Math.min(ls, cap);
        float k = (float) ls / Math.max(cw, ch);
        int w = Math.max(16, Math.round(cw * k)), h = Math.max(16, Math.round(ch * k));
        if (mp4) { w &= ~1; h &= ~1; }
        return new int[]{w, h};
    }

    int[] dimsFor(int f, int sizeVal) {
        if (f <= 1) return exportDims(sizeVal);
        if (f == 2) return new int[]{cv.canvasW, cv.canvasH};
        return animDims(f == 4, sizeVal);
    }

    TextView choiceChip(String t, int padDp) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(DARK);
        v.setTextSize(13);
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(padDp), dp(10), dp(padDp), dp(10));
        v.setBackgroundResource(R.drawable.glass_chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMarginEnd(dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    void setupSheetWindow(Dialog d) {
        Window win = d.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setLayout(MATCH, WRAP);
            win.setGravity(Gravity.BOTTOM);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(0.45f);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    void animateSheetIn(final View root, final Dialog d) {
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

    void showExport() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final View root = getLayoutInflater().inflate(R.layout.dialog_export, null);
        d.setContentView(root);
        d.setCanceledOnTouchOutside(true);
        setupSheetWindow(d);
        final ImageView prev = root.findViewById(R.id.exPreview);
        final TextView info = root.findViewById(R.id.exInfo);
        final TextView bgInfo = root.findViewById(R.id.exBg);
        final TextView ext = root.findViewById(R.id.exExt);
        final TextView save = root.findViewById(R.id.exSave);
        final TextView share = root.findViewById(R.id.exShare);
        final EditText name = root.findViewById(R.id.exName);
        final LinearLayout fRow = root.findViewById(R.id.exFormatRow);
        final LinearLayout sRow = root.findViewById(R.id.exSizeRow);
        final LinearLayout dRow = root.findViewById(R.id.exDurRow);
        final View sLabel = root.findViewById(R.id.exSizeLabel);
        final View dLabel = root.findViewById(R.id.exDurLabel);
        name.setText(defaultName());

        final String[] fNames = {"PNG", "JPG", "SVG", "GIF", "MP4"};
        final int[] sizes = {0, 1080, 2048, 4096};
        final String[] sLabels = {"Canvas", "1080", "2048", "4096"};
        final int[] durs = {2, 3, 5};
        final int[] fmt = {0}, sz = {0}, du = {1};
        final TextView[] fChips = new TextView[5];
        final TextView[] sChips = new TextView[4];
        final TextView[] dChips = new TextView[3];

        final Runnable refresh = () -> {
            int f = fmt[0];
            for (int i = 0; i < 5; i++)
                fChips[i].setBackgroundResource(i == f ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
            for (int i = 0; i < 4; i++)
                sChips[i].setBackgroundResource(i == sz[0] ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
            for (int i = 0; i < 3; i++)
                dChips[i].setBackgroundResource(i == du[0] ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
            boolean anim = f >= 3;
            sLabel.setVisibility(f == 2 ? View.GONE : View.VISIBLE);
            sRow.setVisibility(f == 2 ? View.GONE : View.VISIBLE);
            dLabel.setVisibility(anim ? View.VISIBLE : View.GONE);
            dRow.setVisibility(anim ? View.VISIBLE : View.GONE);
            int eff = ((f == 1 || f == 4) && cv.bgColor == 0) ? 0xFFFFFFFF : cv.bgColor;
            int[] dm = dimsFor(f, sizes[sz[0]]);
            String extra = anim ? " \u00b7 " + durs[du[0]] + "s" : (f == 2 ? " \u00b7 vector" : "");
            info.setText(fNames[f] + " \u00b7 " + dm[0] + " \u00d7 " + dm[1] + (f == 2 ? "" : " px") + extra);
            ext.setText(EXTS[f]);
            if (f == 2) bgInfo.setText("Opens in Figma, Illustrator, Inkscape");
            else if (anim) bgInfo.setText("Draws layer by layer" + (eff == 0 ? " \u00b7 transparent" : ""));
            else bgInfo.setText(eff == 0 ? "Transparent background" : "Solid background");
            prev.setImageBitmap(renderPreview(eff));
        };

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView c = choiceChip(fNames[i], 10);
            c.setOnClickListener(v -> { fmt[0] = idx; pop(v); refresh.run(); });
            fChips[i] = c;
            fRow.addView(c);
        }
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView c = choiceChip(sLabels[i], 12);
            c.setOnClickListener(v -> { sz[0] = idx; pop(v); refresh.run(); });
            sChips[i] = c;
            sRow.addView(c);
        }
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView c = choiceChip(durs[i] + " s", 14);
            c.setOnClickListener(v -> { du[0] = idx; pop(v); refresh.run(); });
            dChips[i] = c;
            dRow.addView(c);
        }
        refresh.run();

        save.setOnClickListener(v -> {
            int[] dm = dimsFor(fmt[0], sizes[sz[0]]);
            exportNow(d, root, save, share, save, false, fmt[0], dm[0], dm[1], durs[du[0]], name.getText().toString());
        });
        share.setOnClickListener(v -> {
            int[] dm = dimsFor(fmt[0], sizes[sz[0]]);
            exportNow(d, root, save, share, share, true, fmt[0], dm[0], dm[1], durs[du[0]], name.getText().toString());
        });
        animateSheetIn(root, d);
    }

    void dismissSheet(final Dialog d, View root) {
        root.animate().translationY(root.getHeight()).alpha(0f).setDuration(220)
                .withEndAction(() -> { if (d.isShowing()) d.dismiss(); }).start();
    }

    /** Draw-on animation frame: layers are drawn one after another. */
    Bitmap animFrame(ArrayList<Layer> snap, float prog, int w, int h, int bg, float aw, float ah) {
        int vis = 0;
        for (Layer l : snap) if (l.visible) vis++;
        float e = prog < 0.5f ? 4f * prog * prog * prog : 1f - (float) Math.pow(-2f * prog + 2f, 3) / 2f;
        float t = e * Math.max(1, vis);
        int j = 0;
        for (Layer l : snap) {
            if (!l.visible) continue;
            l.reveal = Math.max(0f, Math.min(1f, t - j));
            j++;
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (bg != 0) c.drawColor(bg);
        float unit = Math.min(w, h);
        for (Layer l : snap) Renderer.draw(c, l, unit, aw, ah, 0f, 0f, null, w, h);
        return b;
    }

    Uri saveWith(String name, String ext, String mime, boolean video, OutWriter w) {
        ContentResolver r = getContentResolver();
        Uri u = null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ext);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, video ? "Movies/Curvo" : "Pictures/Curvo");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            u = r.insert(video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            OutputStream os = new BufferedOutputStream(r.openOutputStream(u), 65536);
            w.write(os);
            os.flush();
            os.close();
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            r.update(u, v, null, null);
            return u;
        } catch (Exception e) {
            if (u != null) {
                try { r.delete(u, null, null); } catch (Exception ignored) { }
            }
            return null;
        }
    }

    void exportNow(final Dialog d, final View root, final TextView save, final TextView share,
                   final TextView btn, final boolean doShare, final int f,
                   final int ew, final int eh, final int durSec, String rawName) {
        if (exporting) return;
        exporting = true;
        String n = rawName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (n.isEmpty()) n = defaultName();
        final String fileName = n;
        final CharSequence old = btn.getText();
        btn.setText("Saving...");
        save.setAlpha(0.7f);
        share.setAlpha(0.7f);
        final int eff = ((f == 1 || f == 4) && cv.bgColor == 0) ? 0xFFFFFFFF : cv.bgColor;
        final ArrayList<Layer> snap = cloneAll(cv.layers);
        final float aw = cv.aw(), ah = cv.ah();
        final int cw = cv.canvasW, ch = cv.canvasH;
        new Thread(() -> {
            Uri u = null;
            boolean oom = false;
            final String ext = EXTS[f];
            try {
                switch (f) {
                    case 0:
                    case 1: {
                        final Bitmap b = Renderer.export(snap, ew, eh, eff, aw, ah);
                        final boolean jpg = f == 1;
                        u = saveWith(fileName, ext, jpg ? "image/jpeg" : "image/png", false,
                                os -> b.compress(jpg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, 100, os));
                        b.recycle();
                        break;
                    }
                    case 2: {
                        final String svg = SvgWriter.build(snap, cw, ch, eff, aw, ah);
                        u = saveWith(fileName, ext, "image/svg+xml", false, os -> os.write(svg.getBytes("UTF-8")));
                        break;
                    }
                    case 3: {
                        final int fps = 14;
                        final int body = durSec * fps;
                        final int total = body + Math.round(fps * 0.8f);
                        u = saveWith(fileName, ext, "image/gif", false, os -> {
                            GifEncoder ge = new GifEncoder(os, ew, eh);
                            for (int i = 0; i < total; i++) {
                                Bitmap fb = animFrame(snap, Math.min(1f, i / (float) body), ew, eh, eff, aw, ah);
                                ge.addFrame(fb, 7);
                                fb.recycle();
                                final int pct = (i + 1) * 100 / total;
                                runOnUiThread(() -> btn.setText("Rendering " + pct + "%"));
                            }
                            ge.finish();
                        });
                        break;
                    }
                    case 4: {
                        final int fps = 30;
                        final int body = durSec * fps;
                        final int total = body + Math.round(fps * 0.8f);
                        final File tmp = new File(getCacheDir(), "curvo_tmp.mp4");
                        VideoEncoder.encode(tmp, ew, eh, fps, total,
                                i -> animFrame(snap, Math.min(1f, i / (float) body), ew, eh, eff, aw, ah),
                                pct -> runOnUiThread(() -> btn.setText("Rendering " + pct + "%")));
                        u = saveWith(fileName, ext, "video/mp4", true, os -> {
                            FileInputStream in = new FileInputStream(tmp);
                            byte[] buf = new byte[65536];
                            int rd;
                            while ((rd = in.read(buf)) > 0) os.write(buf, 0, rd);
                            in.close();
                        });
                        tmp.delete();
                        break;
                    }
                    default:
                        break;
                }
            } catch (OutOfMemoryError e) {
                oom = true;
                u = null;
            } catch (Throwable e) {
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
                        dismissSheet(d, root);
                        if (doShare) shareUri(res, f == 0 ? "image/png" : f == 1 ? "image/jpeg" : f == 2 ? "image/svg+xml"
                                : f == 3 ? "image/gif" : "video/mp4");
                    }, 700);
                } else {
                    btn.setText(old);
                    String msg = noMem ? "Not enough memory - try a smaller size"
                            : (f == 4 ? "MP4 failed on this device - try GIF" : "Save failed");
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    void shareUri(Uri u, String mime) {
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType(mime);
        s.putExtra(Intent.EXTRA_STREAM, u);
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(s, "Share"));
    }

    // ---------------------------------------------------------------- project JSON
    static String bmpToB64(Bitmap b) {
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

    JSONObject layerToJson(Layer l, boolean masks) throws Exception {
        JSONObject o = new JSONObject();
        o.put("nm", l.name);
        o.put("v", l.visible);
        o.put("lk", l.locked);
        JSONArray pa = new JSONArray(), wa = new JSONArray();
        for (float[] p : l.pts) {
            pa.put((double) p[0]);
            pa.put((double) p[1]);
            wa.put((double) (p.length > 2 ? p[2] : 1f));
        }
        o.put("p", pa);
        o.put("pw", wa);
        o.put("c1", l.c1); o.put("c2", l.c2); o.put("oc", l.outlineColor);
        JSONArray ma = new JSONArray();
        for (int c : l.mid) ma.put(c);
        o.put("mid", ma);
        o.put("gm", l.gradMode); o.put("ga", (double) l.gradAngle);
        o.put("w", (double) l.width); o.put("op", (double) l.opacity);
        o.put("sm", (double) l.smooth); o.put("tp", (double) l.taper);
        o.put("d", (double) l.dash); o.put("g", (double) l.glow);
        o.put("ol", (double) l.outline); o.put("r", l.round);
        o.put("sh", (double) l.shadow); o.put("sd", (double) l.shDist); o.put("sb", (double) l.shBlur);
        o.put("bl", l.blend); o.put("sy", l.sym);
        o.put("pt", l.pat); o.put("ps", (double) l.patScale); o.put("pc", l.patColor);
        if (masks && l.mask != null) o.put("mk", bmpToB64(l.mask));
        return o;
    }

    Layer layerFromJson(JSONObject o) throws Exception {
        Layer l = new Layer();
        l.name = o.optString("nm", "Line");
        l.visible = o.optBoolean("v", true);
        l.locked = o.optBoolean("lk", false);
        JSONArray pa = o.getJSONArray("p");
        JSONArray wa = o.optJSONArray("pw");
        for (int k = 0, j = 0; k + 1 < pa.length(); k += 2, j++) {
            float wm = wa != null && j < wa.length() ? (float) wa.optDouble(j, 1.0) : 1f;
            l.pts.add(new float[]{(float) pa.getDouble(k), (float) pa.getDouble(k + 1), wm});
        }
        l.c1 = o.optInt("c1", l.c1); l.c2 = o.optInt("c2", l.c2); l.outlineColor = o.optInt("oc", l.outlineColor);
        JSONArray ma = o.optJSONArray("mid");
        if (ma != null) for (int i = 0; i < ma.length() && i < 3; i++) l.mid.add(ma.getInt(i));
        l.gradMode = o.optInt("gm", 0);
        l.gradAngle = (float) o.optDouble("ga", 0.0);
        l.width = (float) o.optDouble("w", 0.07); l.opacity = (float) o.optDouble("op", 1.0);
        l.smooth = (float) o.optDouble("sm", 0.5); l.taper = (float) o.optDouble("tp", 0.0);
        l.dash = (float) o.optDouble("d", 0.0); l.glow = (float) o.optDouble("g", 0.0);
        l.outline = (float) o.optDouble("ol", 0.0); l.round = o.optBoolean("r", false);
        l.shadow = (float) o.optDouble("sh", 0.0);
        l.shDist = (float) o.optDouble("sd", 0.5);
        l.shBlur = (float) o.optDouble("sb", 0.5);
        l.blend = o.optInt("bl", 0);
        l.sym = o.optInt("sy", 0);
        l.pat = o.optInt("pt", 0);
        l.patScale = (float) o.optDouble("ps", 0.5);
        l.patColor = o.optInt("pc", 0xFFFFFFFF);
        String mk = o.optString("mk", "");
        if (!mk.isEmpty()) {
            byte[] bytes = Base64.decode(mk, Base64.DEFAULT);
            l.mask = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
        return l.pts.size() >= 2 ? l : null;
    }

    JSONObject stateToJson(boolean masks) throws Exception {
        JSONObject st = new JSONObject();
        st.put("fmt", 2);
        st.put("name", projName);
        st.put("cw", cv.canvasW);
        st.put("ch", cv.canvasH);
        st.put("bg", cv.bgColor);
        st.put("active", cv.active);
        JSONArray arr = new JSONArray();
        for (Layer l : cv.layers) arr.put(layerToJson(l, masks));
        st.put("layers", arr);
        return st;
    }

    boolean applyJson(String json) {
        try {
            JSONObject st = new JSONObject(json);
            JSONArray arr = st.getJSONArray("layers");
            ArrayList<Layer> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                Layer l = layerFromJson(arr.getJSONObject(i));
                if (l != null) out.add(l);
            }
            if (out.isEmpty()) return false;
            cv.layers = out;
            cv.active = Math.max(0, Math.min(st.optInt("active", 0), out.size() - 1));
            cv.canvasW = Math.max(64, Math.min(4096, st.optInt("cw", 2048)));
            cv.canvasH = Math.max(64, Math.min(4096, st.optInt("ch", 2048)));
            cv.bgColor = st.optInt("bg", 0);
            projName = st.optString("name", "Untitled");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- auto-save (last session)
    void saveSession() {
        try {
            getSharedPreferences("curvo", 0).edit()
                    .putString("state", stateToJson(false).toString())
                    .putString("pid", projId)
                    .apply();
        } catch (Exception ignored) { }
    }

    void loadSession() {
        SharedPreferences sp = getSharedPreferences("curvo", 0);
        String st = sp.getString("state", null);
        if (st != null) {
            if (applyJson(st)) projId = sp.getString("pid", null);
            return;
        }
        try {   // sessions saved by older versions
            cv.bgColor = sp.getInt("bg", 0);
            cv.canvasW = sp.getInt("cw", 2048);
            cv.canvasH = sp.getInt("ch", 2048);
            String s = sp.getString("s", null);
            if (s == null) return;
            JSONArray arr = new JSONArray(s);
            ArrayList<Layer> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                Layer l = layerFromJson(arr.getJSONObject(i));
                if (l != null) out.add(l);
            }
            if (!out.isEmpty()) {
                cv.layers = out;
                cv.active = Math.min(sp.getInt("a", 0), out.size() - 1);
            }
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- projects
    void afterLoad() {
        undoS.clear();
        redoS.clear();
        cv.fitView();
        refreshLayers();
        refreshEdit();
        updateRatioUI();
        updateBackdropSel();
        updateUndo();
        cv.invalidate();
    }

    String safeName(String n) {
        String t = n.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return t.isEmpty() ? "Curvo" : t;
    }

    void saveProject(String name, boolean asNew, boolean quiet) {
        try {
            if (asNew || projId == null) projId = ProjectStore.newId();
            projName = name;
            ProjectStore.Meta m = new ProjectStore.Meta();
            m.id = projId;
            m.name = name;
            m.modified = System.currentTimeMillis();
            m.w = cv.canvasW;
            m.h = cv.canvasH;
            Bitmap th = renderPreview(cv.bgColor);
            ProjectStore.save(this, m, stateToJson(true).toString(), th);
            th.recycle();
            if (!quiet) Toast.makeText(this, "Saved \"" + name + "\"", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            if (!quiet) Toast.makeText(this, "Could not save project", Toast.LENGTH_SHORT).show();
        }
    }

    void openProject(String id) {
        try {
            if (projId != null) saveProject(projName, false, true);
            String json = ProjectStore.load(this, id);
            if (applyJson(json)) {
                projId = id;
                afterLoad();
                Toast.makeText(this, "Opened \"" + projName + "\"", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Could not open project", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable e) {
            Toast.makeText(this, "Could not open project", Toast.LENGTH_SHORT).show();
        }
    }

    void newProject() {
        if (projId != null) saveProject(projName, false, true);
        ArrayList<Layer> fresh = new ArrayList<>();
        fresh.add(Layer.defaultLayer());
        cv.layers = fresh;
        cv.active = 0;
        cv.canvasW = 2048;
        cv.canvasH = 2048;
        cv.bgColor = 0;
        projId = null;
        projName = "Untitled";
        afterLoad();
    }

    Uri saveCurvoFile(String name, String json) {
        try {
            ContentResolver r = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName(name) + ".curvo");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Curvo");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri u = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            OutputStream os = r.openOutputStream(u);
            os.write(json.getBytes("UTF-8"));
            os.close();
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            r.update(u, v, null, null);
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    void backupCurrent() {
        try {
            Uri u = saveCurvoFile(projName, stateToJson(true).toString());
            if (u == null) { Toast.makeText(this, "Backup failed", Toast.LENGTH_SHORT).show(); return; }
            Toast.makeText(this, "Saved to Downloads/Curvo", Toast.LENGTH_SHORT).show();
            shareUri(u, "application/octet-stream");
        } catch (Throwable e) {
            Toast.makeText(this, "Backup failed", Toast.LENGTH_SHORT).show();
        }
    }

    void shareProject(ProjectStore.Meta m) {
        try {
            Uri u = saveCurvoFile(m.name, ProjectStore.load(this, m.id));
            if (u == null) { Toast.makeText(this, "Could not create file", Toast.LENGTH_SHORT).show(); return; }
            shareUri(u, "application/octet-stream");
        } catch (Throwable e) {
            Toast.makeText(this, "Could not create file", Toast.LENGTH_SHORT).show();
        }
    }

    void importCurvo(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String text = bo.toString("UTF-8");
            if (projId != null) saveProject(projName, false, true);
            if (!applyJson(text)) {
                Toast.makeText(this, "This is not a Curvo file", Toast.LENGTH_SHORT).show();
                return;
            }
            projId = null;
            saveProject(projName, true, true);
            afterLoad();
            Toast.makeText(this, "Imported \"" + projName + "\"", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show();
        }
    }

    void showProjects() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final View root = getLayoutInflater().inflate(R.layout.dialog_projects, null);
        d.setContentView(root);
        d.setCanceledOnTouchOutside(true);
        setupSheetWindow(d);
        final TextView curT = root.findViewById(R.id.prCurrent);
        final LinearLayout list = root.findViewById(R.id.prList);
        LinearLayout actions = root.findViewById(R.id.prActions);
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> fillProjectList(d, curT, list, refresh);

        actions.addView(chip("Save", v -> {
            pop(v);
            if (projId == null) {
                promptName("Save project", projName, nm -> { saveProject(nm, true, false); refresh[0].run(); });
            } else {
                saveProject(projName, false, false);
                refresh[0].run();
            }
        }));
        actions.addView(chip("Save as", v -> {
            pop(v);
            promptName("Save as", projName, nm -> { saveProject(nm, true, false); refresh[0].run(); });
        }));
        actions.addView(chip("New", v -> {
            pop(v);
            if (projId == null) {
                new AlertDialog.Builder(this).setTitle("Start a new project?")
                        .setMessage("The current drawing is not saved as a project.")
                        .setPositiveButton("Start new", (dl, w) -> { newProject(); d.dismiss(); })
                        .setNegativeButton("Cancel", null).show();
            } else {
                newProject();
                d.dismiss();
            }
        }));
        actions.addView(chip("Open .curvo", v -> {
            pop(v);
            d.dismiss();
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            startActivityForResult(i, REQ_OPEN);
        }));
        actions.addView(chip("Backup .curvo", v -> { pop(v); backupCurrent(); }));
        refresh[0].run();
        animateSheetIn(root, d);
    }

    void fillProjectList(final Dialog d, TextView curT, LinearLayout list, final Runnable[] refresh) {
        list.removeAllViews();
        curT.setText(projName + (projId == null ? " (unsaved)" : ""));
        ArrayList<ProjectStore.Meta> all = ProjectStore.list(this);
        if (all.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("No saved projects yet. Tap Save to keep this design.");
            e.setTextSize(13);
            e.setTextColor(MUTED);
            e.setPadding(dp(2), dp(12), 0, dp(12));
            list.addView(e);
            return;
        }
        SimpleDateFormat df = new SimpleDateFormat("d MMM, HH:mm", Locale.US);
        for (final ProjectStore.Meta m : all) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(8), dp(6), dp(8));
            row.setBackgroundResource(m.id.equals(projId) ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, WRAP);
            rl.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(rl);
            ImageView th = new ImageView(this);
            th.setScaleType(ImageView.ScaleType.FIT_CENTER);
            th.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
            Bitmap tb = ProjectStore.thumb(this, m.id);
            if (tb != null) th.setImageBitmap(tb);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, WRAP, 1f);
            cl.setMarginStart(dp(12));
            col.setLayoutParams(cl);
            TextView nm = new TextView(this);
            nm.setText(m.name);
            nm.setTextSize(15);
            nm.setTextColor(DARK);
            nm.setTypeface(null, Typeface.BOLD);
            nm.setSingleLine(true);
            TextView meta = new TextView(this);
            meta.setText(df.format(new Date(m.modified)) + " \u00b7 " + m.w + " \u00d7 " + m.h);
            meta.setTextSize(11);
            meta.setTextColor(MUTED);
            col.addView(nm);
            col.addView(meta);
            ImageButton shareB = iconBtn(R.drawable.ic_share, 38);
            ImageButton delB = iconBtn(R.drawable.ic_delete, 38);
            row.addView(th);
            row.addView(col);
            row.addView(shareB);
            row.addView(delB);
            row.setOnClickListener(v -> { d.dismiss(); openProject(m.id); });
            row.setOnLongClickListener(v -> {
                promptName("Rename project", m.name, t -> {
                    try { ProjectStore.rename(this, m.id, t); } catch (Exception ignored) { }
                    if (m.id.equals(projId)) projName = t;
                    refresh[0].run();
                });
                return true;
            });
            shareB.setOnClickListener(v -> shareProject(m));
            delB.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Delete project?").setMessage(m.name)
                    .setPositiveButton("Delete", (dl, w) -> {
                        ProjectStore.delete(this, m.id);
                        if (m.id.equals(projId)) projId = null;
                        refresh[0].run();
                    })
                    .setNegativeButton("Cancel", null).show());
            list.addView(row);
        }
        TextView hint = new TextView(this);
        hint.setText("Tap to open \u00b7 hold to rename");
        hint.setTextSize(11);
        hint.setTextColor(MUTED);
        hint.setPadding(dp(2), dp(6), 0, 0);
        list.addView(hint);
    }
}
