package com.tanzirdev.curvo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends Activity {
    static final int DARK = 0xFF0B3D2E, GREEN = 0xFF0F9D75, REQ_MASK = 11;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int[] PAL = {0xFF000000, 0xFFFFFFFF, 0xFF64748B, 0xFF0F9D75, 0xFF34D399, 0xFFA3E635, 0xFFFACC15,
            0xFFF97316, 0xFFEF4444, 0xFFEC4899, 0xFF8B5CF6, 0xFF2563EB, 0xFF0EA5E9, 0xFF7DD3FC, 0xFF78350F};

    interface Getter { float g(Layer l); }
    interface Setter { void s(Layer l, float v); }
    static class Row { SeekBar sb; Getter g; }
    static class Snap {
        ArrayList<Layer> l; int a;
        Snap(ArrayList<Layer> src, int a) { l = cloneAll(src); this.a = a; }
    }

    CurveView cv;
    ImageButton bUndo, bRedo, bLayers, bExport, bVector, bPreset, bColor, bEdit, bMask, bAddLayer;
    View pPreset, pEdit, pLayers;
    LinearLayout pColor, presetRow, editBox, layerList;
    TextView chipRound;
    TextView[] tChips = new TextView[3];
    int curPanel = -1, target = 0;
    float density;
    final ArrayList<Snap> undoS = new ArrayList<>(), redoS = new ArrayList<>();
    final ArrayList<Row> rows = new ArrayList<>();
    final Random rnd = new Random();

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
        bMask = findViewById(R.id.bMask); bAddLayer = findViewById(R.id.bAddLayer);
        pPreset = findViewById(R.id.pPreset); pColor = findViewById(R.id.pColor);
        pEdit = findViewById(R.id.pEdit); pLayers = findViewById(R.id.pLayers);
        presetRow = findViewById(R.id.presetRow); editBox = findViewById(R.id.editBox);
        layerList = findViewById(R.id.layerList);

        ImageButton[] all = {bUndo, bRedo, bLayers, bExport, bVector, bPreset, bColor, bEdit, bMask, bAddLayer};
        for (ImageButton ib : all) ib.setColorFilter(DARK);

        loadSession();
        if (cv.layers.isEmpty()) { cv.layers.add(Layer.defaultLayer()); cv.active = 0; }

        cv.listener = new CurveView.Listener() {
            @Override public void onBeforeChange() { push(); }
            @Override public void onChanged() { }
        };

        buildPresets();
        buildColorPanel();
        buildEditPanel();
        refreshLayers();
        refreshEdit();
        updateUndo();
        highlight(bVector, cv.vectorMode);

        bUndo.setOnClickListener(v -> undo());
        bRedo.setOnClickListener(v -> redo());
        bLayers.setOnClickListener(v -> showPanel(3));
        bExport.setOnClickListener(v -> exportDialog());
        bPreset.setOnClickListener(v -> showPanel(0));
        bColor.setOnClickListener(v -> showPanel(1));
        bEdit.setOnClickListener(v -> showPanel(2));
        bVector.setOnClickListener(v -> {
            cv.vectorMode = !cv.vectorMode;
            highlight(bVector, cv.vectorMode);
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

    void highlight(ImageButton b, boolean on) { b.setColorFilter(on ? GREEN : DARK); }

    void showPanel(int id) {
        View[] ps = {pPreset, pColor, pEdit, pLayers};
        for (View p : ps) p.setVisibility(View.GONE);
        if (id == curPanel) curPanel = -1;
        else { ps[id].setVisibility(View.VISIBLE); curPanel = id; }
        highlight(bPreset, curPanel == 0);
        highlight(bColor, curPanel == 1);
        highlight(bEdit, curPanel == 2);
        highlight(bLayers, curPanel == 3);
    }

    // ---------------------------------------------------------------- undo / redo
    void push() {
        undoS.add(new Snap(cv.layers, cv.active));
        if (undoS.size() > 60) undoS.remove(0);
        redoS.clear();
        updateUndo();
    }

    void undo() {
        if (undoS.isEmpty()) return;
        redoS.add(new Snap(cv.layers, cv.active));
        Snap s = undoS.remove(undoS.size() - 1);
        cv.layers = s.l; cv.active = s.a;
        afterRestore();
    }

    void redo() {
        if (redoS.isEmpty()) return;
        undoS.add(new Snap(cv.layers, cv.active));
        Snap s = redoS.remove(redoS.size() - 1);
        cv.layers = s.l; cv.active = s.a;
        afterRestore();
    }

    void afterRestore() {
        cv.invalidate();
        refreshEdit();
        refreshLayers();
        updateUndo();
    }

    void updateUndo() {
        bUndo.setAlpha(undoS.isEmpty() ? 0.35f : 1f);
        bRedo.setAlpha(redoS.isEmpty() ? 0.35f : 1f);
    }

    // ---------------------------------------------------------------- presets
    void buildPresets() {
        for (Presets.P p : Presets.ALL) {
            PresetThumb t = new PresetThumb(this, p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(76));
            lp.setMargins(dp(5), 0, dp(5), 0);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> {
                Layer l = cur();
                if (l == null) return;
                push();
                Presets.apply(l, p);
                cv.invalidate();
                refreshEdit();
            });
            presetRow.addView(t);
        }
    }

    // ---------------------------------------------------------------- colour panel
    TextView chip(String t, View.OnClickListener c) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(DARK);
        v.setTextSize(12);
        v.setPadding(dp(12), dp(7), dp(12), dp(7));
        v.setBackgroundResource(R.drawable.glass_chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        v.setLayoutParams(lp);
        v.setOnClickListener(c);
        return v;
    }

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

    void updateTargetChips() {
        for (int i = 0; i < 3; i++)
            tChips[i].setBackgroundResource(i == target ? R.drawable.glass_chip_sel : R.drawable.glass_chip);
    }

    void buildColorPanel() {
        LinearLayout targets = new LinearLayout(this);
        String[] names = {"Start", "End", "Edge"};
        for (int i = 0; i < 3; i++) {
            final int ti = i;
            tChips[i] = chip(names[i], v -> { target = ti; updateTargetChips(); });
            targets.addView(tChips[i]);
        }
        targets.addView(chip("Solid", v -> {
            Layer l = cur(); if (l == null) return;
            push(); l.c2 = l.c1; cv.invalidate();
        }));
        targets.addView(chip("Swap", v -> {
            Layer l = cur(); if (l == null) return;
            push(); int t = l.c1; l.c1 = l.c2; l.c2 = t; cv.invalidate();
        }));
        HorizontalScrollView th = new HorizontalScrollView(this);
        th.setHorizontalScrollBarEnabled(false);
        th.addView(targets);
        pColor.addView(th);
        updateTargetChips();

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, dp(4));
        hs.addView(row);
        for (int c : PAL) {
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.setMargins(dp(4), dp(2), dp(4), dp(2));
            v.setLayoutParams(lp);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(dp(1), 0x33000000);
            v.setBackground(g);
            v.setOnClickListener(x -> { push(); setTargetColor(c); });
            row.addView(v);
        }
        pColor.addView(hs);

        pColor.addView(simpleSeek(360, (sb, p, user) -> {
            if (!user) return;
            float[] hsv = new float[3];
            Color.colorToHSV(getTargetColor(), hsv);
            float s = hsv[1] < 0.15f ? 0.7f : hsv[1];
            float vv = hsv[2] < 0.3f ? 0.8f : hsv[2];
            setTargetColor(Color.HSVToColor(new float[]{p, s, vv}));
        }));
        pColor.addView(simpleSeek(100, (sb, p, user) -> {
            if (!user) return;
            float[] hsv = new float[3];
            Color.colorToHSV(getTargetColor(), hsv);
            setTargetColor(Color.HSVToColor(new float[]{hsv[0], 0.7f, 0.25f + 0.75f * p / 100f}));
        }));
    }

    interface SeekCb { void on(SeekBar sb, int p, boolean user); }

    SeekBar simpleSeek(int max, SeekCb cb) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        tintSeek(sb);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { cb.on(s, p, u); }
            @Override public void onStartTrackingTouch(SeekBar s) { push(); }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        return sb;
    }

    void tintSeek(SeekBar sb) {
        ColorStateList g = ColorStateList.valueOf(GREEN);
        sb.setProgressTintList(g);
        sb.setThumbTintList(g);
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(0x330B3D2E));
    }

    // ---------------------------------------------------------------- edit panel
    void addSlider(String label, Getter g, Setter s) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(DARK);
        t.setTextSize(12);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(62), WRAP));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
        tintSeek(sb);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar x, int p, boolean user) {
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
        editBox.addView(r);
        Row row = new Row();
        row.sb = sb; row.g = g;
        rows.add(row);
    }

    void buildEditPanel() {
        addSlider("Width", l -> (l.width - 0.005f) / 0.195f, (l, v) -> l.width = 0.005f + v * 0.195f);
        addSlider("Opacity", l -> l.opacity, (l, v) -> l.opacity = Math.max(0.05f, v));
        addSlider("Smooth", l -> l.smooth, (l, v) -> l.smooth = v);
        addSlider("Taper", l -> l.taper, (l, v) -> l.taper = Math.min(v, 0.95f));
        addSlider("Dash", l -> l.dash, (l, v) -> l.dash = v);
        addSlider("Glow", l -> l.glow, (l, v) -> l.glow = v);
        addSlider("Outline", l -> l.outline, (l, v) -> l.outline = v);

        LinearLayout chips = new LinearLayout(this);
        chipRound = chip("Flat ends", v -> {
            Layer l = cur(); if (l == null) return;
            push(); l.round = !l.round; cv.invalidate(); refreshEdit();
        });
        chips.addView(chipRound);
        chips.addView(chip("Flip H", v -> flip(true)));
        chips.addView(chip("Flip V", v -> flip(false)));
        chips.addView(chip("Random", v -> {
            Layer l = cur(); if (l == null) return;
            push(); l.randomize(rnd); cv.invalidate();
        }));
        chips.addView(chip("No mask", v -> clearMask()));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        editBox.addView(hs);
    }

    void flip(boolean horizontal) {
        Layer l = cur();
        if (l == null) return;
        push();
        for (float[] p : l.pts) { if (horizontal) p[0] = 1f - p[0]; else p[1] = 1f - p[1]; }
        cv.invalidate();
    }

    void refreshEdit() {
        Layer l = cur();
        if (l == null) return;
        for (Row r : rows) r.sb.setProgress(Math.round(Math.max(0f, Math.min(1f, r.g.g(l))) * 100));
        if (chipRound != null) chipRound.setText(l.round ? "Round ends" : "Flat ends");
        highlight(bMask, l.mask != null);
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
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
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
                for (float[] p : c.pts) p[1] = Math.min(1f, p[1] + 0.05f);
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
        n.randomize(rnd);
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

    // ---------------------------------------------------------------- export
    void exportDialog() {
        String[] items = {"PNG - transparent", "JPG - white", "JPG - black"};
        new AlertDialog.Builder(this).setItems(items, (d, w) -> doExport(w)).show();
    }

    void doExport(int w) {
        final boolean png = w == 0;
        final int bg = w == 0 ? 0 : (w == 1 ? 0xFFFFFFFF : 0xFF000000);
        final ArrayList<Layer> snap = cloneAll(cv.layers);
        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Bitmap b = Renderer.export(snap, 2048, bg);
            boolean ok = save(b, png);
            b.recycle();
            runOnUiThread(() -> Toast.makeText(this,
                    ok ? "Saved to Pictures/Curvo" : "Save failed", Toast.LENGTH_SHORT).show());
        }).start();
    }

    boolean save(Bitmap b, boolean png) {
        try {
            ContentValues v = new ContentValues();
            String name = "Curvo_" + System.currentTimeMillis() + (png ? ".png" : ".jpg");
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, png ? "image/png" : "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Curvo");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            ContentResolver r = getContentResolver();
            Uri u = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return false;
            OutputStream os = r.openOutputStream(u);
            b.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 100, os);
            os.close();
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            r.update(u, v, null, null);
            return true;
        } catch (Exception e) {
            return false;
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
                arr.put(o);
            }
            getSharedPreferences("curvo", 0).edit()
                    .putString("s", arr.toString()).putInt("a", cv.active).apply();
        } catch (Exception ignored) { }
    }

    void loadSession() {
        try {
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
                if (l.pts.size() >= 2) out.add(l);
            }
            if (!out.isEmpty()) {
                cv.layers = out;
                cv.active = Math.min(getSharedPreferences("curvo", 0).getInt("a", 0), out.size() - 1);
            }
        } catch (Exception ignored) { }
    }
}
