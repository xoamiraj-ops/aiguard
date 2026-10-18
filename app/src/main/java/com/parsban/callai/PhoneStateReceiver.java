package com.parsban.callai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class PhoneStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) return;

        AppDb db = new AppDb(context);
        long id = AppPrefs.activeSession(context);
        CallSession s = id > 0 ? db.get(id) : db.latestOpen();

        if (s == null) {
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                RecordingInboxWorker.enqueueSoon(context, 12);
            }
            return;
        }

        long now = System.currentTimeMillis();

        if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            if (s.connectedAt <= 0L) s.connectedAt = now;
            s.status = "Connected";
            db.update(s);
            return;
        }

        if (!TelephonyManager.EXTRA_STATE_IDLE.equals(state)) return;

        s.endedAt = now;
        AppPrefs.clearActiveSession(context);

        if (s.connectedAt <= 0L) {
            s.status = "Missed";
            db.update(s);
            RecordingInboxWorker.enqueueSoon(context, 12);
            return;
        }

        s.status = "WaitingRecording";
        db.update(s);

        Data data = new Data.Builder().putLong("sessionId", s.id).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RecordingMatchWorker.class)
                .setInputData(data)
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueue(request);

        RecordingInboxWorker.enqueueSoon(context, 18);
    }
}
