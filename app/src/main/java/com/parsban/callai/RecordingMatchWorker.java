package com.parsban.callai;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RecordingMatchWorker extends Worker {
    public RecordingMatchWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    @NonNull
    @Override
    public Result doWork() {
        long id = getInputData().getLong("sessionId", -1L);
        if (id <= 0) return Result.failure();

        Context c = getApplicationContext();
        AppDb db = new AppDb(c);
        CallSession s = db.get(id);
        if (s == null) return Result.failure();

        if (AppPrefs.recordingTree(c).isEmpty()) {
            s.status = "NeedsRecordingFolder";
            s.error = "پوشه ضبط مکالمات سامسونگ انتخاب نشده";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        }

        Uri recording = RecordingScanner.findBest(c, s);
        if (recording == null) {
            if (getRunAttemptCount() < 4) return Result.retry();
            s.status = "RecordingNotFound";
            s.error = "فایل ضبط این تماس پیدا نشد";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        }

        s.recordingUri = recording.toString();
        s.error = "";

        String policy = PolicyStore.get(c, s.phone);
        if (AppPrefs.POLICY_NEVER.equals(policy)) {
            s.status = "Skipped";
            db.update(s);
            return Result.success();
        }
        if (AppPrefs.POLICY_ASK.equals(policy)) {
            s.status = "AwaitingApproval";
            db.update(s);
            NotificationHelper.showApproval(c, s.id, s.contactName);
            return Result.success();
        }

        s.status = "QueuedForAI";
        db.update(s);
        ProcessCallWorker.enqueue(c, s.id);
        return Result.success();
    }
}
