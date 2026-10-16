package com.parsban.callai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

public class CrmLookupWorker extends Worker {
    public CrmLookupWorker(@NonNull Context c, @NonNull WorkerParameters p) {
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

        try {
            JSONObject response = new GatewayClient(getApplicationContext()).lookup(s.phone);
            JSONObject match = response.optJSONObject("match");

            if (match != null) {
                s.crmType = match.optString("type", "");
                s.crmId = match.optString("id", "");
                s.customerName = match.optString("name", "");
                db.update(s);
                NotificationHelper.updateCustomer(
                        getApplicationContext(), s.id, s.phone, s.customerName);
            }
            return Result.success();
        } catch (Exception e) {
            // Caller identification must never interfere with the phone call.
            return Result.success();
        }
    }
}
