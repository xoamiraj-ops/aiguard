package com.parsban.callai;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.Locale;

public final class RecordingScanner {
    private RecordingScanner() {}

    public static Uri findBest(Context context, CallSession session) {
        String rawTree = AppPrefs.recordingTree(context);
        if (rawTree == null || rawTree.isEmpty()) return null;

        DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(rawTree));
        if (root == null || !root.exists()) return null;

        Candidate best = new Candidate();
        int[] count = new int[]{0};
        scan(context, root, session, best, count, 0);
        return best.uri;
    }

    private static void scan(
            Context context,
            DocumentFile node,
            CallSession session,
            Candidate best,
            int[] count,
            int depth
    ) {
        if (depth > 3 || count[0] > 800) return;

        DocumentFile[] children;
        try {
            children = node.listFiles();
        } catch (Exception e) {
            return;
        }

        for (DocumentFile file : children) {
            if (count[0]++ > 800) return;

            if (file.isDirectory()) {
                scan(context, file, session, best, count, depth + 1);
                continue;
            }

            String name = file.getName();
            if (!isAudio(name, file.getType())) continue;

            long modified = file.lastModified();
            long end = session.endedAt > 0 ? session.endedAt : System.currentTimeMillis();

            if (modified > 0L && Math.abs(modified - end) > 10L * 60L * 1000L) {
                continue;
            }

            double score = 0.0;

            if (modified > 0L) {
                long diffSec = Math.abs(modified - end) / 1000L;
                score += Math.max(0, 300 - diffSec);
            } else {
                score += 20;
            }

            String needle = PhoneUtil.lastDigits(session.phone, 7);
            String digitsName = PhoneUtil.digits(name == null ? "" : name);
            if (!needle.isEmpty() && digitsName.contains(needle)) score += 300;

            long callDuration = session.durationMs();
            long audioDuration = duration(context, file.getUri());
            if (callDuration > 0 && audioDuration > 0) {
                long diff = Math.abs(callDuration - audioDuration);
                if (diff < 15_000L) score += 180;
                else if (diff < 35_000L) score += 90;
                else if (diff < 90_000L) score += 20;
            }

            if (score > best.score) {
                best.score = score;
                best.uri = file.getUri();
            }
        }
    }

    private static boolean isAudio(String name, String mime) {
        if (mime != null && mime.toLowerCase(Locale.US).startsWith("audio/")) return true;
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        return n.endsWith(".m4a") || n.endsWith(".amr") || n.endsWith(".3gp")
                || n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".ogg");
    }

    private static long duration(Context context, Uri uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(context, uri);
            String raw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return raw == null ? 0L : Long.parseLong(raw);
        } catch (Exception e) {
            return 0L;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    private static final class Candidate {
        Uri uri;
        double score = -1.0;
    }
}
