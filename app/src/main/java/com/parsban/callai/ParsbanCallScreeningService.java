package com.parsban.callai;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class ParsbanCallScreeningService extends CallScreeningService {
    @Override
    public void onScreenCall(Call.Details details) {
        boolean incoming = details.getCallDirection() == Call.Details.DIRECTION_INCOMING;

        if (incoming) {
            respondToCall(details, new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build());
        }

        Uri handle = details.getHandle();
        if (handle == null || !"tel".equals(handle.getScheme())) return;

        String phone = handle.getSchemeSpecificPart();
        if (phone == null || phone.trim().isEmpty()) return;

        CallSession s = new CallSession();
        s.phone = phone;
        s.direction = incoming ? "Incoming" : "Outgoing";
        s.createdAt = System.currentTimeMillis();
        s.status = incoming ? "Ringing" : "Dialing";

        AppDb db = new AppDb(getApplicationContext());
        s.id = db.insert(s);
        AppPrefs.setActiveSession(this, s.id);

        NotificationHelper.showIncoming(this, s.id, phone, "");

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data data = new Data.Builder().putLong("sessionId", s.id).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CrmLookupWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(this).enqueue(request);
    }
}
