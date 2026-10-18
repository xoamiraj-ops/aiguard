package com.parsban.callai;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RecordingScanner {
    private RecordingScanner() {}

    public static Uri findBest(Context context, CallSession session) {
        DocumentFile root = root(context);
        if (root == null) return null;

        Candidate best = new Candidate();
        int[] count = new int[]{0};
        scanForCall(context, root, session, best, count, 0);
        return best.uri;
    }

    public static Uri findNewest(Context context) {
        List<RecordingInfo> items = recent(context, 1);
        return items.isEmpty() ? null : items.get(0).uri;
    }

    public static List<RecordingInfo> recent(Context context, int limit) {
        DocumentFile root = root(context);
        if (root == null) return Collections.emptyList();
        List<RecordingInfo> out = new ArrayList<>();
        int[] count = new int[]{0};
        collectRecent(context, root, out, count, 0);
        out.sort((a, b) -> Long.compare(b.modified, a.modified));
        if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }

    private static DocumentFile root(Context context) {
        String rawTree = AppPrefs.recordingTree(context);
        if (rawTree == null || rawTree.isEmpty()) return null;
        DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(rawTree));
        return root != null && root.exists() ? root : null;
    }

    private static void collectRecent(Context context, DocumentFile node, List<RecordingInfo> out, int[] count, int depth) {
        if (depth > 5 || count[0] > 2000) return;
        DocumentFile[] children;
        try { children = node.listFiles(); } catch (Exception e) { return; }
        long cutoff = System.currentTimeMillis() - 14L * 24L * 60L * 60L * 1000L;

        for (DocumentFile file : children) {
            if (count[0]++ > 2000) return;
            if (file.isDirectory()) {
                collectRecent(context, file, out, count, depth + 1);
                continue;
            }
            if (!isAudio(file.getName(), file.getType())) continue;
            long modified = file.lastModified();
            if (modified > 0L && modified < cutoff) continue;
            out.add(new RecordingInfo(
                    file.getUri(),
                    file.getName() == null ? "" : file.getName(),
                    modified > 0L ? modified : System.currentTimeMillis(),
                    duration(context, file.getUri())
            ));
        }
    }

    private static void scanForCall(
            Context context,
            DocumentFile node,
            CallSession session,
            Candidate best,
            int[] count,
            int depth
    ) {
        if (depth > 5 || count[0] > 1500) return;
        DocumentFile[] children;
        try { children = node.listFiles(); } catch (Exception e) { return; }

        for (DocumentFile file : children) {
            if (count[0]++ > 1500) return;
            if (file.isDirectory()) {
                scanForCall(context, file, session, best, count, depth + 1);
                continue;
            }
            String name = file.getName();
            if (!isAudio(name, file.getType())) continue;

            long modified = file.lastModified();
            long end = session.endedAt > 0 ? session.endedAt : System.currentTimeMillis();
            if (modified > 0L && Math.abs(modified - end) > 15L * 60L * 1000L) continue;

            double score = 0.0;
            if (modified > 0L) {
                long diffSec = Math.abs(modified - end) / 1000L;
                score += Math.max(0, 420 - diffSec);
            } else {
                score += 20;
            }

            String needle = PhoneUtil.lastDigits(session.phone, 7);
            String digitsName = PhoneUtil.digits(name == null ? "" : name);
            if (!needle.isEmpty() && digitsName.contains(needle)) score += 350;

            if (session.contactName != null && !session.contactName.isEmpty() && name != null) {
                String compactName = session.contactName.replace(" ", "").toLowerCase(Locale.getDefault());
                String compactFile = name.replace(" ", "").toLowerCase(Locale.getDefault());
                if (compactName.length() >= 3 && compactFile.contains(compactName)) score += 220;
            }

            long callDuration = session.durationMs();
            long audioDuration = duration(context, file.getUri());
            if (callDuration > 0 && audioDuration > 0) {
                long diff = Math.abs(callDuration - audioDuration);
                if (diff < 12_000L) score += 220;
                else if (diff < 30_000L) score += 120;
                else if (diff < 75_000L) score += 40;
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
                || n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".ogg")
                || n.endsWith(".aac") || n.endsWith(".mp4");
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

    public static final class RecordingInfo {
        public final Uri uri;
        public final String name;
        public final long modified;
        public final long durationMs;

        RecordingInfo(Uri uri, String name, long modified, long durationMs) {
            this.uri = uri;
            this.name = name;
            this.modified = modified;
            this.durationMs = durationMs;
        }
    }
}
