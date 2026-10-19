package com.parsban.callai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordingInboxWorker extends Worker {
    private static final String PERIODIC = "avakav-recording-inbox-periodic";
    private static final String ONCE = "avakav-recording-inbox-once";
    private static final Pattern IR_MOBILE = Pattern.compile("(?<!\\d)(?:\\+98|0098|98|0)?9\\d{9}(?!\\d)");
    private static final Pattern IR_LOCAL = Pattern.compile("(?<!\\d)0\\d{10}(?!\\d)");

    public RecordingInboxWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    public static void ensureScheduled(Context c) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                RecordingInboxWorker.class, 15, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    public static void enqueueSoon(Context c, long delaySeconds) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(RecordingInboxWorker.class)
                .setInitialDelay(Math.max(0L, delaySeconds), TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(c).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.REPLACE, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (AppPrefs.recordingTree(c).isEmpty()) return Result.success();

        AppDb db = new AppDb(c);
        List<RecordingScanner.RecordingInfo> recordings = RecordingScanner.recent(c, 40);

        for (RecordingScanner.RecordingInfo info : recordings) {
            String uri = info.uri.toString();
            if (db.hasRecordingUri(uri)) continue;

            CallSession s = db.findWaitingNear(info.recordedAt);
            if (s == null) {
                s = new CallSession();
                s.createdAt = info.recordedAt;
                s.connectedAt = Math.max(0L, info.recordedAt);
                s.endedAt = info.durationMs > 0L
                        ? info.recordedAt + info.durationMs
                        : info.recordedAt;
                s.direction = "Imported";
                s.status = "QueuedForAI";
                s.phone = phoneFromName(info.name);
                s.contactName = ContactResolver.nameFor(c, s.phone);
                if (s.contactName.isEmpty()) s.contactName = labelFromName(info.name, s.phone);
            }

            s.recordingUri = uri;
            if (s.createdAt <= 0L) s.createdAt = info.recordedAt;
            if (s.connectedAt <= 0L) s.connectedAt = info.recordedAt;
            if (s.endedAt <= 0L) {
                s.endedAt = info.durationMs > 0L
                        ? info.recordedAt + info.durationMs
                        : info.recordedAt;
            }

            String policy = PolicyStore.get(c, s.phone);
            if (AppPrefs.POLICY_NEVER.equals(policy)) s.status = "Skipped";
            else if (AppPrefs.POLICY_ASK.equals(policy)) s.status = "AwaitingApproval";
            else s.status = "QueuedForAI";

            if (s.id > 0L) db.update(s);
            else s.id = db.insert(s);

            if ("QueuedForAI".equals(s.status)) ProcessCallWorker.enqueue(c, s.id);
        }

        return Result.success();
    }

    private static String phoneFromName(String name) {
        String n = name == null ? "" : name;

        Matcher m = IR_MOBILE.matcher(n);
        if (m.find()) return PhoneUtil.normalizeIran(m.group());

        m = IR_LOCAL.matcher(n);
        if (m.find()) return PhoneUtil.normalizeIran(m.group());

        return "";
    }

    private static String labelFromName(String name, String phone) {
        String n = name == null ? "" : name;
        n = n.replaceFirst("(?i)\\.(m4a|amr|3gp|mp3|wav|ogg|aac|mp4)$", "");
        n = n.replaceFirst("(?i)^call\\s+", "");
        n = n.replaceFirst("_[0-9]{6}_[0-9]{6}$", "");

        if (phone != null && !phone.isEmpty()) {
            String last = PhoneUtil.lastDigits(phone, 7);
            if (!last.isEmpty()) n = n.replace(last, "");
        }

        n = n.replaceAll("[_-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (n.length() > 64) n = n.substring(0, 64).trim();
        if (n.isEmpty()) return "ضبط سامسونگ";
        return n;
    }
}
