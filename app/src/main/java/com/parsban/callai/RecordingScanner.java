package com.parsban.callai;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecordingScanner {
    private static final Pattern SAMSUNG_TIME =
            Pattern.compile("_(\\d{6})_(\\d{6})(?:\\.[^.]+)?$");

    private RecordingScanner() {}

    public static Uri findBest(Context context, CallSession session) {
        DocumentFile root = root(context);
        if (root == null) return null;

        Candidate best = new Candidate();
        int[] count = new int[]{0};
        scanForCall(context, root, session, best, count, 0);

        // Do not guess. A weak candidate is more dangerous than returning no match.
        return best.score >= 350.0 ? best.uri : null;
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
        out.sort((a, b) -> Long.compare(b.recordedAt, a.recordedAt));

        if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }

    private static DocumentFile root(Context context) {
        String rawTree = AppPrefs.recordingTree(context);
        if (rawTree == null || rawTree.isEmpty()) return null;
        DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(rawTree));
        return root != null && root.exists() ? root : null;
    }

    private static void collectRecent(
            Context context,
            DocumentFile node,
            List<RecordingInfo> out,
            int[] count,
            int depth
    ) {
        if (depth > 5 || count[0] > 2500) return;

        DocumentFile[] children;
        try { children = node.listFiles(); } catch (Exception e) { return; }

        long cutoff = System.currentTimeMillis() - 21L * 24L * 60L * 60L * 1000L;

        for (DocumentFile file : children) {
            if (count[0]++ > 2500) return;

            if (file.isDirectory()) {
                collectRecent(context, file, out, count, depth + 1);
                continue;
            }

            String name = file.getName();
            if (!isAudio(name, file.getType())) continue;

            long modified = file.lastModified();
            long fromName = samsungTime(name);
            long recordedAt = fromName > 0L
                    ? fromName
                    : (modified > 0L ? modified : System.currentTimeMillis());

            if (recordedAt < cutoff) continue;

            out.add(new RecordingInfo(
                    file.getUri(),
                    name == null ? "" : name,
                    modified,
                    recordedAt,
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
        if (depth > 5 || count[0] > 2500) return;

        DocumentFile[] children;
        try { children = node.listFiles(); } catch (Exception e) { return; }

        for (DocumentFile file : children) {
            if (count[0]++ > 2500) return;

            if (file.isDirectory()) {
                scanForCall(context, file, session, best, count, depth + 1);
                continue;
            }

            String name = file.getName();
            if (!isAudio(name, file.getType())) continue;

            double score = 0.0;
            long fileNameTime = samsungTime(name);
            long modified = file.lastModified();

            if (fileNameTime > 0L) {
                long diff = bestSessionTimeDiff(session, fileNameTime);

                // Samsung embeds call time in the file name. If it is far from this
                // call, reject it even when lastModified happens to be recent.
                if (diff > 30L * 60L * 1000L) continue;

                long diffSec = diff / 1000L;
                score += Math.max(0.0, 900.0 - (diffSec * 1.5));
            } else {
                long anchor = session.endedAt > 0L ? session.endedAt : session.createdAt;
                if (anchor <= 0L) anchor = System.currentTimeMillis();

                if (modified <= 0L || Math.abs(modified - anchor) > 20L * 60L * 1000L) {
                    continue;
                }

                long diffSec = Math.abs(modified - anchor) / 1000L;
                score += Math.max(0.0, 300.0 - diffSec);
            }

            String needle = PhoneUtil.lastDigits(session.phone, 7);
            String digitsName = PhoneUtil.digits(name == null ? "" : name);
            if (!needle.isEmpty() && digitsName.contains(needle)) score += 750.0;

            if (session.contactName != null && !session.contactName.isEmpty() && name != null) {
                String compactName = normalizeLabel(session.contactName);
                String compactFile = normalizeLabel(name);

                if (compactName.length() >= 3) {
                    if (compactFile.contains(compactName)) {
                        score += 650.0;
                    } else {
                        String[] tokens = normalizeWords(session.contactName).split(" ");
                        int matched = 0;
                        int meaningful = 0;
                        for (String token : tokens) {
                            if (token.length() < 2) continue;
                            meaningful++;
                            if (compactFile.contains(token)) matched++;
                        }
                        if (meaningful > 0 && matched >= Math.min(2, meaningful)) {
                            score += 260.0;
                        }
                    }
                }
            }

            long callDuration = session.durationMs();
            long audioDuration = duration(context, file.getUri());
            if (callDuration > 0L && audioDuration > 0L) {
                long diff = Math.abs(callDuration - audioDuration);
                if (diff < 10_000L) score += 320.0;
                else if (diff < 25_000L) score += 200.0;
                else if (diff < 60_000L) score += 80.0;
                else if (diff > 5L * 60L * 1000L) score -= 150.0;
            }

            if (score > best.score) {
                best.score = score;
                best.uri = file.getUri();
            }
        }
    }

    private static long bestSessionTimeDiff(CallSession s, long fileTime) {
        long best = Long.MAX_VALUE;
        if (s.createdAt > 0L) best = Math.min(best, Math.abs(fileTime - s.createdAt));
        if (s.connectedAt > 0L) best = Math.min(best, Math.abs(fileTime - s.connectedAt));
        if (s.endedAt > 0L) best = Math.min(best, Math.abs(fileTime - s.endedAt));
        return best == Long.MAX_VALUE ? 0L : best;
    }

    public static long samsungTime(String name) {
        if (name == null || name.isEmpty()) return 0L;

        Matcher m = SAMSUNG_TIME.matcher(name);
        if (!m.find()) return 0L;

        String raw = m.group(1) + "_" + m.group(2);
        SimpleDateFormat f = new SimpleDateFormat("yyMMdd_HHmmss", Locale.US);
        f.setLenient(false);

        ParsePosition pos = new ParsePosition(0);
        Date d = f.parse(raw, pos);
        if (d == null || pos.getIndex() != raw.length()) return 0L;
        return d.getTime();
    }

    private static String normalizeLabel(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.getDefault())
                .replaceFirst("^call\\s+", "")
                .replaceFirst("_[0-9]{6}_[0-9]{6}$", "")
                .replaceAll("(?i)\\.(m4a|amr|3gp|mp3|wav|ogg|aac|mp4)$", "")
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static String normalizeWords(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.getDefault())
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
        public final long recordedAt;
        public final long durationMs;

        RecordingInfo(Uri uri, String name, long modified, long recordedAt, long durationMs) {
            this.uri = uri;
            this.name = name;
            this.modified = modified;
            this.recordedAt = recordedAt;
            this.durationMs = durationMs;
        }
    }
}
