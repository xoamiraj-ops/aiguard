package com.parsban.callai;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class RecordingMatchWorker extends Worker {
    public RecordingMatchWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    @NonNull
    @Override
    public Result doWork() {
        long id = getInputData().getLong("sessionId", -1L);
        if (id <= 0) return Result.failure();

        AppDb db = new AppDb(getApplicationContext());
        CallSession s = db.get(id);
        if (s == null) return Result.failure();

        if (AppPrefs.recordingTree(getApplicationContext()).isEmpty()) {
            s.status = "NeedsRecordingFolder";
            s.error = "پوشه ضبط سامسونگ انتخاب نشده";
            db.update(s);
            NotificationHelper.showError(getApplicationContext(), s.id, s.error);
            return Result.failure();
        }

        Uri recording = RecordingScanner.findBest(getApplicationContext(), s);
        if (recording == null) {
            if (getRunAttemptCount() < 4) return Result.retry();
            s.status = "RecordingNotFound";
            s.error = "فایل ضبط این تماس پیدا نشد";
            db.update(s);
            NotificationHelper.showError(getApplicationContext(), s.id, s.error);
            return Result.failure();
        }

        s.recordingUri = recording.toString();
        s.status = "QueuedForAI";
        s.error = "";
        db.update(s);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data data = new Data.Builder().putLong("sessionId", s.id).build();
        OneTimeWorkRequest process = new OneTimeWorkRequest.Builder(ProcessCallWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(process);
        return Result.success();
    }
}
