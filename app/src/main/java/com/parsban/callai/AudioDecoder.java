package com.parsban.callai;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.SystemClock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class AudioDecoder {
    private static final int TARGET_RATE = 16000;
    private static final int MAX_SECONDS = 60 * 60;
    private AudioDecoder() {}

    public static float[] decode16kMono(Context context, Uri uri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        FloatBuffer mono = new FloatBuffer(1024 * 1024);
        try {
            extractor.setDataSource(context, uri, null);
            int track = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { track = i; inputFormat = f; break; }
            }
            if (track < 0 || inputFormat == null) throw new IOException("ترک صوتی داخل فایل پیدا نشد");
            extractor.selectTrack(track);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IOException("فرمت صوتی ناشناخته است");

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(inputFormat, null, null, 0);
            codec.start();

            boolean inputDone = false, outputDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
            int channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;

            long lastProgress = SystemClock.elapsedRealtime();
            while (!outputDone) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("تبدیل صوت متوقف شد");
                if (SystemClock.elapsedRealtime() - lastProgress > 30_000L)
                    throw new IOException("دیکودر صوت ۳۰ ثانیه خروجی نداد؛ فایل ضبط را دوباره انتخاب کنید");
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer in = codec.getInputBuffer(inIndex);
                        if (in == null) throw new IOException("بافر ورودی دیکودر در دسترس نیست");
                        int size = extractor.readSampleData(in, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            // Extractor flags and codec flags are different namespaces.
                            // In particular PARTIAL_FRAME (4) must never become END_OF_STREAM (4).
                            codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat out = codec.getOutputFormat();
                    if (out.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if (outIndex >= 0) {
                    lastProgress = SystemClock.elapsedRealtime();
                    ByteBuffer out = codec.getOutputBuffer(outIndex);
                    if (out != null && info.size > 0) {
                        ByteBuffer data = out.duplicate().order(ByteOrder.nativeOrder());
                        data.position(info.offset); data.limit(info.offset + info.size);
                        appendMono(mono, data.slice().order(ByteOrder.nativeOrder()), channels, pcmEncoding);
                        if (mono.size() > sampleRate * MAX_SECONDS) throw new IOException("در نسخه آزمایشی، مکالمه‌های بیشتر از ۶۰ دقیقه پشتیبانی نمی‌شوند");
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outIndex, false);
                }
            }
            float[] source = mono.toArray();
            if (source.length == 0) throw new IOException("صدای قابل پردازش از فایل استخراج نشد");
            return resample(source, sampleRate, TARGET_RATE);
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                try { codec.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static void appendMono(FloatBuffer dst, ByteBuffer data, int channels, int encoding) throws IOException {
        int ch = Math.max(1, channels);
        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            int frames = data.remaining() / (4 * ch);
            for (int f = 0; f < frames; f++) {
                float sum = 0f;
                for (int c = 0; c < ch; c++) sum += data.getFloat();
                dst.add(clamp(sum / ch));
            }
            return;
        }
        if (encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == 0) {
            int frames = data.remaining() / (2 * ch);
            for (int f = 0; f < frames; f++) {
                float sum = 0f;
                for (int c = 0; c < ch; c++) sum += data.getShort() / 32768f;
                dst.add(clamp(sum / ch));
            }
            return;
        }
        throw new IOException("فرمت PCM خروجی دیکودر پشتیبانی نمی‌شود: " + encoding);
    }

    private static float clamp(float x) { return Math.max(-1f, Math.min(1f, x)); }

    private static float[] resample(float[] src, int sourceRate, int targetRate) {
        if (sourceRate <= 0 || sourceRate == targetRate) return src;
        int outLength = Math.max(1, (int) Math.round(src.length * (targetRate / (double) sourceRate)));
        float[] out = new float[outLength];
        double step = sourceRate / (double) targetRate;
        for (int i = 0; i < outLength; i++) {
            double pos = i * step;
            int a = Math.min(src.length - 1, (int) pos);
            int b = Math.min(src.length - 1, a + 1);
            float t = (float) (pos - a);
            out[i] = src[a] + (src[b] - src[a]) * t;
        }
        return out;
    }

    private static final class FloatBuffer {
        private float[] data; private int size;
        FloatBuffer(int initial) { data = new float[Math.max(1024, initial)]; }
        void add(float v) {
            if (size == data.length) data = Arrays.copyOf(data, data.length + Math.max(1024, data.length / 2));
            data[size++] = v;
        }
        int size() { return size; }
        float[] toArray() { return Arrays.copyOf(data, size); }
    }
}

