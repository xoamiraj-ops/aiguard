package com.parsban.callai;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import android.content.pm.ServiceInfo;
import java.util.concurrent.locks.ReentrantLock;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class ProcessCallWorker extends Worker {
    private static final ReentrantLock ENGINE_LOCK = new ReentrantLock();
    public ProcessCallWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    public static void enqueue(Context c, long sessionId) {
        Data data = new Data.Builder().putLong("sessionId", sessionId).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ProcessCallWorker.class)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(c).enqueueUniqueWork("avakav-analysis-" + sessionId, ExistingWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        long id = getInputData().getLong("sessionId", -1L);
        if (id <= 0L) return Result.failure();

        Context c = getApplicationContext();
        AppDb db = new AppDb(c);
        CallSession s = db.get(id);

        if (s == null) return Result.failure();

        if (!ENGINE_LOCK.tryLock()) return Result.retry();
        try {
        return process(c, db, s);
        } finally { ENGINE_LOCK.unlock(); }
    }

    private Result process(Context c, AppDb db, CallSession s) {
        // Re-check the Samsung folder before processing. This repairs old sessions
        // that were associated with a wrong file by earlier AvaKav builds.
        Uri best = RecordingScanner.findBest(c, s);
        if (best != null) {
            String bestRaw = best.toString();
            if (!bestRaw.equals(s.recordingUri) && !db.hasRecordingUri(bestRaw)) {
                s.recordingUri = bestRaw;
                s.error = "";
                db.update(s);
            }
        }

        if (s.recordingUri == null || s.recordingUri.isEmpty()) {
            s.status = "RecordingNotFound";
            s.error = "فایل ضبط درست این تماس پیدا نشد";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        }

        if (!LocalModels.ready(c)) {
            s.status = "NeedsLocalModels";
            s.error = "مدل‌های آفلاین آواکاو هنوز دانلود نشده‌اند";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        }

        s.status = "DecodingAudio";
        s.error = "";
        db.update(s);

        try {
            setForegroundAsync(new ForegroundInfo((int) (12000 + s.id),
                    NotificationHelper.processing(c, s.id), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)).get();
            AnalysisProgress progress = stage -> {
                if (isStopped()) throw new IllegalStateException("پردازش توسط اندروید متوقف شد؛ دوباره تحلیل کنید");
                s.status = stage;
                db.update(s);
            };
            String transcript = LocalWhisperEngine.transcribeBlocking(c, Uri.parse(s.recordingUri), progress);

            s.transcript = transcript;
            s.aiModel = "Whisper Small Q5_1 • Local";
            db.update(s);

            s.status = "SummarizingLocal";
            db.update(s);
            LocalAnalysis a = LocalLlmEngine.analyzeBlocking(c, s, transcript);

            s.summary = a.getSummary();
            s.highlights = a.getHighlightsJson();
            s.actionItems = a.getActionItemsJson();
            s.aiModel = "Whisper Small Q5_1 + Qwen2.5 0.5B Q4_K_M • 100% Local";
            s.status = "Completed";
            s.error = "";
            db.update(s);

            NotificationHelper.showProcessed(c, s.id, s.contactName, s.summary);
            return Result.success();

        } catch (OutOfMemoryError oom) {
            s.status = "Failed";
            s.error = "حافظه گوشی برای این مکالمه کافی نبود. برنامه‌های سنگین را ببند و دوباره تلاش کن.";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();

        } catch (LinkageError e) {
            s.status = "Failed";
            s.error = "خطا در بارگذاری موتور محلی: " + e.getClass().getSimpleName();
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        } catch (Exception e) {
            s.error = e.getMessage() == null
                    ? "خطا در پردازش محلی مکالمه"
                    : e.getMessage();

            s.status = "Failed";
            db.update(s);
            NotificationHelper.showError(c, s.id, s.error);
            return Result.failure();
        }
    }
}

