package com.tanzirdev.curvo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/** Saves projects in the app's private folder: id.json (design), id.meta (info) and id.png (thumbnail). */
public class ProjectStore {
    public static class Meta {
        public String id, name;
        public long modified;
        public int w, h;
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "projects");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String newId() { return "p" + System.currentTimeMillis(); }

    public static ArrayList<Meta> list(Context c) {
        ArrayList<Meta> out = new ArrayList<>();
        File[] fs = dir(c).listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            String n = f.getName();
            if (!n.endsWith(".meta")) continue;
            try {
                JSONObject o = new JSONObject(readAll(f));
                Meta m = new Meta();
                m.id = n.substring(0, n.length() - 5);
                m.name = o.optString("name", "Untitled");
                m.modified = o.optLong("mod", 0L);
                m.w = o.optInt("w", 0);
                m.h = o.optInt("h", 0);
                out.add(m);
            } catch (Exception ignored) { }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }

    public static void save(Context c, Meta m, String json, Bitmap thumb) throws Exception {
        File d = dir(c);
        writeAll(new File(d, m.id + ".json"), json.getBytes("UTF-8"));
        JSONObject o = new JSONObject();
        o.put("name", m.name);
        o.put("mod", m.modified);
        o.put("w", m.w);
        o.put("h", m.h);
        writeAll(new File(d, m.id + ".meta"), o.toString().getBytes("UTF-8"));
        if (thumb != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            thumb.compress(Bitmap.CompressFormat.PNG, 90, bo);
            writeAll(new File(d, m.id + ".png"), bo.toByteArray());
        }
    }

    public static String load(Context c, String id) throws IOException {
        return readAll(new File(dir(c), id + ".json"));
    }

    public static void rename(Context c, String id, String name) throws Exception {
        File f = new File(dir(c), id + ".meta");
        JSONObject o = new JSONObject(readAll(f));
        o.put("name", name);
        writeAll(f, o.toString().getBytes("UTF-8"));
    }

    public static void delete(Context c, String id) {
        File d = dir(c);
        new File(d, id + ".json").delete();
        new File(d, id + ".meta").delete();
        new File(d, id + ".png").delete();
    }

    public static Bitmap thumb(Context c, String id) {
        File f = new File(dir(c), id + ".png");
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    private static String readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private static void writeAll(File f, byte[] data) throws IOException {
        FileOutputStream o = new FileOutputStream(f);
        try {
            o.write(data);
        } finally {
            o.close();
        }
    }
}
