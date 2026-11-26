package com.tanzirdev.curvo;

import android.graphics.Bitmap;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/** Minimal animated GIF writer: fixed 3-3-2 palette, transparent index 255, looping. */
public class GifEncoder {
    private final OutputStream out;
    private final int w, h;
    private int nBits, maxcode, freeEnt;
    private BitOut bo;

    public GifEncoder(OutputStream out, int w, int h) throws IOException {
        this.out = out;
        this.w = w;
        this.h = h;
        out.write("GIF89a".getBytes("US-ASCII"));
        writeShort(w);
        writeShort(h);
        out.write(0xF7);
        out.write(0);
        out.write(0);
        for (int i = 0; i < 256; i++) {
            int r = (i >> 5) & 7, g = (i >> 2) & 7, b = i & 3;
            out.write(r * 255 / 7);
            out.write(g * 255 / 7);
            out.write(b * 255 / 3);
        }
        out.write(0x21); out.write(0xFF); out.write(11);
        out.write("NETSCAPE2.0".getBytes("US-ASCII"));
        out.write(3); out.write(1); writeShort(0); out.write(0);
    }

    private void writeShort(int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    public void addFrame(Bitmap bmp, int delayCs) throws IOException {
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        byte[] idx = new byte[w * h];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            if ((p >>> 24) < 128) {
                idx[i] = (byte) 255;
            } else {
                int v = (((p >> 16) & 255) >> 5 << 5) | (((p >> 8) & 255) >> 5 << 2) | ((p & 255) >> 6);
                if (v == 255) v = 254;
                idx[i] = (byte) v;
            }
        }
        out.write(0x21); out.write(0xF9); out.write(4);
        out.write((2 << 2) | 1);
        writeShort(delayCs);
        out.write(255);
        out.write(0);
        out.write(0x2C);
        writeShort(0); writeShort(0); writeShort(w); writeShort(h);
        out.write(0);
        lzw(idx);
    }

    public void finish() throws IOException {
        out.write(0x3B);
        out.flush();
    }

    private void emit(int code) throws IOException {
        bo.write(code, nBits);
        if (freeEnt > maxcode) {
            nBits++;
            maxcode = nBits == 12 ? 4096 : (1 << nBits) - 1;
        }
    }

    private void clearCode() throws IOException {
        bo.write(256, nBits);
        nBits = 9;
        maxcode = 511;
        freeEnt = 258;
    }

    private void lzw(byte[] pix) throws IOException {
        out.write(8);
        final int hsize = 16384;
        int[] keys = new int[hsize];
        int[] vals = new int[hsize];
        Arrays.fill(keys, -1);
        bo = new BitOut(out);
        nBits = 9;
        maxcode = 511;
        freeEnt = 258;
        bo.write(256, nBits);
        int ent = pix[0] & 0xFF;
        for (int i = 1; i < pix.length; i++) {
            int c = pix[i] & 0xFF;
            int fcode = (c << 12) + ent;
            int hh = ((fcode * 0x9E3779B1) >>> 18) & (hsize - 1);
            boolean found = false;
            while (keys[hh] != -1) {
                if (keys[hh] == fcode) { found = true; break; }
                hh = (hh + 1) & (hsize - 1);
            }
            if (found) { ent = vals[hh]; continue; }
            emit(ent);
            ent = c;
            if (freeEnt < 4096) {
                keys[hh] = fcode;
                vals[hh] = freeEnt++;
            } else {
                Arrays.fill(keys, -1);
                clearCode();
            }
        }
        emit(ent);
        bo.write(257, nBits);
        bo.flush();
        out.write(0);
    }

    private static class BitOut {
        private final OutputStream o;
        private final byte[] buf = new byte[255];
        private int cnt = 0, accum = 0, bits = 0;
        BitOut(OutputStream o) { this.o = o; }

        void write(int code, int n) throws IOException {
            accum |= code << bits;
            bits += n;
            while (bits >= 8) {
                put(accum & 0xFF);
                accum >>>= 8;
                bits -= 8;
            }
        }

        private void put(int b) throws IOException {
            buf[cnt++] = (byte) b;
            if (cnt == 255) flushBlock();
        }

        private void flushBlock() throws IOException {
            if (cnt > 0) {
                o.write(cnt);
                o.write(buf, 0, cnt);
                cnt = 0;
            }
        }

        void flush() throws IOException {
            if (bits > 0) { put(accum & 0xFF); accum = 0; bits = 0; }
            flushBlock();
        }
    }
}
