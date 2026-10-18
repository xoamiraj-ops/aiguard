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

public class ProcessCallWorker extends Worker {
    public ProcessCallWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    public static void enqueue(Context c, long sessionId) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data data = new Data.Builder().putLong("sessionId", sessionId).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ProcessCallWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(c).enqueue(request);
    }

    @NonNull
    @Override
    public Result doWork() {
        long id = getInputData().getLong("sessionId", -1L);
        if (id <= 0) return Result.failure();

        Context c = getApplicationContext();
        AppDb db = new AppDb(c);
        CallSession s = db.get(id);
        if (s == null || s.recordingUri == null || s.recordingUri.isEmpty()) return Result.failure();

        if (!AppPrefs.cloudConsent(c)) {
            s.status = "NeedsConsent";
            s.error = "اجازه پردازش ابری در تنظیمات فعال نشده است";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        }
        if (SecretStore.openAiKey(c).isEmpty()) {
            s.status = "NeedsApiKey";
            s.error = "کلید OpenAI در تنظیمات وارد نشده است";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        }

        s.status = "ProcessingAI";
        s.error = "";
        db.update(s);

        try {
            OpenAiClient client = new OpenAiClient(c);
            OpenAiClient.Transcription t = client.transcribe(Uri.parse(s.recordingUri));
            s.transcript = t.text;
            s.aiModel = t.model;
            db.update(s);

            OpenAiClient.Analysis a = client.analyze(s, t.text);
            s.summary = a.summary;
            s.highlights = a.highlightsJson;
            s.actionItems = a.actionItemsJson;
            s.aiModel = t.model + " + " + a.model;
            s.status = "Completed";
            s.error = "";
            db.update(s);

            NotificationHelper.showProcessed(c, s.id, s.contactName, s.summary);
            return Result.success();
        } catch (Exception e) {
            s.error = e.getMessage() == null ? "خطا در پردازش مکالمه" : e.getMessage();
            if (getRunAttemptCount() < 2) {
                s.status = "RetryingAI";
                db.update(s);
                return Result.retry();
            }
            s.status = "Failed";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        }
    }
}
