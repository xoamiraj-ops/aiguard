package com.parsban.callai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

public class ProcessCallWorker extends Worker {
    public ProcessCallWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    @NonNull
    @Override
    public Result doWork() {
        long id = getInputData().getLong("sessionId", -1L);
        if (id <= 0) return Result.failure();

        AppDb db = new AppDb(getApplicationContext());
        CallSession s = db.get(id);
        if (s == null || s.recordingUri == null || s.recordingUri.isEmpty()) {
            return Result.failure();
        }

        s.status = "ProcessingAI";
        s.error = "";
        db.update(s);

        try {
            JSONObject response = new GatewayClient(getApplicationContext()).processCall(s);
            s.crmCallId = response.optString("callId", "");
            s.summary = response.optString("summary", "");
            s.transcript = response.optString("transcript", "");
            String name = response.optString("parentName", "");
            if (!name.isEmpty()) s.customerName = name;
            s.status = "Completed";
            s.error = "";
            db.update(s);

            NotificationHelper.showProcessed(
                    getApplicationContext(), s.id, s.customerName, s.summary);
            return Result.success();
        } catch (Exception e) {
            s.error = e.getMessage() == null ? "خطا در پردازش مکالمه" : e.getMessage();

            if (getRunAttemptCount() < 3) {
                s.status = "RetryingAI";
                db.update(s);
                return Result.retry();
            }

            s.status = "Failed";
            db.update(s);
            NotificationHelper.showError(getApplicationContext(), s.id, s.error);
            return Result.failure();
        }
    }
}
