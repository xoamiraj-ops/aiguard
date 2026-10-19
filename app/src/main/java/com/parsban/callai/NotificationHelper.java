package com.parsban.callai;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class NotificationHelper {
    private static final String CALLS = "avakav_calls";
    private static final String PROCESSING = "avakav_ai";

    private NotificationHelper() {}

    public static Notification processing(Context c, long sessionId) {
        ensure(c);
        return new Notification.Builder(c, PROCESSING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("آواکاو؛ تحلیل محلی مکالمه")
                .setContentText("برای دیدن مرحله پردازش لمس کنید")
                .setContentIntent(open(c, sessionId, (int) (12000 + sessionId)))
                .setOngoing(true).setOnlyAlertOnce(true).build();
    }

    public static void showIncoming(Context c, long sessionId, String phone, String contact) {
        if (!allowed(c)) return;
        ensure(c);
        String title = contact == null || contact.isEmpty() ? "تماس جدید" : contact;
        Notification n = new Notification.Builder(c, CALLS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(phone)
                .setContentIntent(open(c, sessionId, (int) sessionId))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        c.getSystemService(NotificationManager.class).notify((int) (2000 + sessionId), n);
    }

    public static void showApproval(Context c, long sessionId, String contact) {
        if (!allowed(c)) return;
        ensure(c);
        String who = contact == null || contact.isEmpty() ? "این تماس" : contact;
        Notification n = new Notification.Builder(c, PROCESSING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("مکالمه آماده تحلیل است")
                .setContentText("برای تحلیل مکالمه با " + who + " لمس کنید")
                .setContentIntent(open(c, sessionId, (int) (4000 + sessionId)))
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify((int) (4000 + sessionId), n);
    }

    public static void showProcessed(Context c, long sessionId, String contact, String summary) {
        if (!allowed(c)) return;
        ensure(c);
        String who = contact == null || contact.isEmpty() ? "مکالمه" : "مکالمه با " + contact;
        Notification n = new Notification.Builder(c, PROCESSING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("تحلیل " + who + " آماده شد")
                .setContentText(summary == null || summary.isEmpty() ? "خلاصه آماده است" : summary)
                .setStyle(new Notification.BigTextStyle().bigText(summary))
                .setContentIntent(open(c, sessionId, (int) (5000 + sessionId)))
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify((int) (5000 + sessionId), n);
    }

    public static void showError(Context c, long sessionId, String message) {
        if (!allowed(c)) return;
        ensure(c);
        Notification n = new Notification.Builder(c, PROCESSING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("آواکاو نیاز به بررسی دارد")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(open(c, sessionId, (int) (8000 + sessionId)))
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify((int) (8000 + sessionId), n);
    }

    private static PendingIntent open(Context c, long sessionId, int request) {
        Intent i = new Intent(c, CallDetailActivity.class);
        i.putExtra("sessionId", sessionId);
        return PendingIntent.getActivity(
                c, request, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 33 ||
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private static void ensure(Context c) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if (m.getNotificationChannel(CALLS) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CALLS, "تماس‌های آواکاو", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("وضعیت تشخیص تماس");
            m.createNotificationChannel(channel);
        }
        if (m.getNotificationChannel(PROCESSING) == null) {
            NotificationChannel channel = new NotificationChannel(
                    PROCESSING, "تحلیل مکالمات", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("آماده شدن متن، خلاصه و نکات مهم مکالمه");
            m.createNotificationChannel(channel);
        }
    }
}

