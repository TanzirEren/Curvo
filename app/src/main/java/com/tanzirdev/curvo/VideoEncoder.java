package com.tanzirdev.curvo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;

/** H.264 MP4 writer using a Surface encoder. Frames are supplied as bitmaps of exactly w x h. */
public class VideoEncoder {
    public interface FrameSource { Bitmap frame(int index); }
    public interface Progress { void on(int pct); }

    private final MediaCodec enc;
    private final MediaMuxer mux;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private final int fps;
    private int track = -1;
    private boolean started = false;
    private long outCount = 0;

    private VideoEncoder(MediaCodec enc, MediaMuxer mux, int fps) {
        this.enc = enc;
        this.mux = mux;
        this.fps = fps;
    }

    public static void encode(File outFile, int w, int h, int fps, int frames, FrameSource src, Progress pr) throws Exception {
        MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, Math.min(12000000, Math.max(2000000, w * h * 6)));
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        MediaCodec enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface surf = enc.createInputSurface();
        enc.start();
        MediaMuxer mux = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        VideoEncoder ve = new VideoEncoder(enc, mux, fps);
        try {
            for (int i = 0; i < frames; i++) {
                Bitmap b = src.frame(i);
                Canvas c = surf.lockCanvas(null);
                c.drawBitmap(b, 0, 0, null);
                surf.unlockCanvasAndPost(c);
                b.recycle();
                ve.drain(false);
                if (pr != null) pr.on((i + 1) * 100 / frames);
            }
            enc.signalEndOfInputStream();
            ve.drain(true);
        } finally {
            try { enc.stop(); } catch (Exception ignored) { }
            enc.release();
            surf.release();
            try { if (ve.started) mux.stop(); } catch (Exception ignored) { }
            mux.release();
        }
    }

    private void drain(boolean eos) {
        int idle = 0;
        while (true) {
            int idx = enc.dequeueOutputBuffer(info, 10000);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!eos) return;
                if (++idle > 500) return;
            } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = mux.addTrack(enc.getOutputFormat());
                mux.start();
                started = true;
            } else if (idx >= 0) {
                idle = 0;
                ByteBuffer buf = enc.getOutputBuffer(idx);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0 && started && buf != null) {
                    info.presentationTimeUs = outCount * 1000000L / fps;
                    outCount++;
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    mux.writeSampleData(track, buf, info);
                }
                enc.releaseOutputBuffer(idx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
            }
        }
    }
}
