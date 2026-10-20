package com.parsban.callai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;
import java.util.function.LongConsumer;

/** Explicit user-selected recording, also available when folder scanning fails. */
public final class RecordingPicker {
    private static final int REQUEST = 702;
    public static void open(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*"); // Samsung providers sometimes assign an unexpected audio MIME type.
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        a.startActivityForResult(i, REQUEST);
    }

    public static void receive(Activity a, int request, int result, Intent data,
            long sessionId, LongConsumer complete) {
        if (request != REQUEST || result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            a.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            Toast.makeText(a, "دسترسی پایدار فایل داده نشد؛ فایل را از حافظه داخلی انتخاب کن", Toast.LENGTH_LONG).show();
            return;
        }
        new android.app.AlertDialog.Builder(a).setTitle("تحلیل فایل انتخاب‌شده")
                .setMessage("این فایل به مکالمه متصل و روی گوشی تحلیل شود؟")
                .setNegativeButton("لغو", null)
                .setPositiveButton("تحلیل کن", (dialog, which) -> new Thread(() -> {
                    try {
                        long id = attach(a, uri, sessionId);
                        a.runOnUiThread(() -> { if (!a.isFinishing() && !a.isDestroyed()) complete.accept(id); });
                    } catch (Exception e) {
                        a.runOnUiThread(() -> Toast.makeText(a, "انتخاب فایل ناموفق بود: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }, "recording-import").start()).show();
    }

    public static synchronized long attach(Context c, Uri uri, long sessionId) throws Exception {
        try (ParcelFileDescriptor fd = c.getContentResolver().openFileDescriptor(uri, "r")) {
            if (fd == null) throw new IllegalStateException("فایل قابل خواندن نیست");
        }
        try (AppDb db = new AppDb(c)) {
            CallSession existing = db.byRecordingUri(uri.toString());
            if (existing != null) return existing.id;
            CallSession s = sessionId > 0 ? db.get(sessionId) : null;
            if (sessionId > 0 && s == null) throw new IllegalStateException("مکالمه پیدا نشد");
            if (s != null && s.recordingUri != null && !s.recordingUri.isEmpty()) return s.id;
            if (s == null) {
                s = new CallSession();
                s.phone = ""; s.direction = "Manual";
                s.createdAt = System.currentTimeMillis();
                s.connectedAt = s.createdAt; s.endedAt = s.createdAt;
                s.contactName = "ضبط انتخاب‌شده";
            }
            s.recordingUri = uri.toString(); s.error = ""; s.status = "QueuedForAI";
            if (s.id > 0) db.update(s); else s.id = db.insert(s);
            ProcessCallWorker.enqueue(c, s.id);
            return s.id;
        }
    }
}
