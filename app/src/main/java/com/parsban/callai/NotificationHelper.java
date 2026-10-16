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
    private static final String CALLS = "parsban_calls";
    private static final String PROCESSING = "parsban_processing";

    private NotificationHelper() {}

    public static void showIncoming(Context c, long sessionId, String phone, String customer) {
        if (!allowed(c)) return;
        ensure(c);

        String title = customer == null || customer.isEmpty()
                ? "تماس ورودی"
                : "تماس مشتری: " + customer;

        Notification n = new Notification.Builder(c, CALLS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(phone)
                .setContentIntent(openApp(c, (int) sessionId))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setOnlyAlertOnce(false)
                .build();

        c.getSystemService(NotificationManager.class).notify((int) (2000 + sessionId), n);
    }

    public static void updateCustomer(Context c, long sessionId, String phone, String customer) {
        showIncoming(c, sessionId, phone, customer);
    }

    public static void showProcessed(Context c, long sessionId, String customer, String summary) {
        if (!allowed(c)) return;
        ensure(c);

        String title = customer == null || customer.isEmpty()
                ? "مکالمه در CRM ثبت شد"
                : "مکالمه " + customer + " ثبت شد";

        Notification n = new Notification.Builder(c, PROCESSING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(summary == null || summary.isEmpty() ? "پردازش با موفقیت انجام شد" : summary)
                .setStyle(new Notification.BigTextStyle().bigText(summary))
                .setContentIntent(openApp(c, (int) (5000 + sessionId)))
                .setAutoCancel(true)
                .build();

        c.getSystemService(NotificationManager.class).notify((int) (5000 + sessionId), n);
    }

    public static void showError(Context c, long sessionId, String message) {
        if (!allowed(c)) return;
        ensure(c);

        Notification n = new Notification.Builder(c, PROCESSING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("پردازش مکالمه نیاز به بررسی دارد")
                .setContentText(message)
                .setContentIntent(openApp(c, (int) (8000 + sessionId)))
                .setAutoCancel(true)
                .build();

        c.getSystemService(NotificationManager.class).notify((int) (8000 + sessionId), n);
    }

    private static PendingIntent openApp(Context c, int request) {
        return PendingIntent.getActivity(
                c, request, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static void ensure(Context c) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if (m.getNotificationChannel(CALLS) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CALLS, "تشخیص تماس مشتری", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("نمایش مشتری شناسایی‌شده هنگام تماس");
            m.createNotificationChannel(channel);
        }
        if (m.getNotificationChannel(PROCESSING) == null) {
            NotificationChannel channel = new NotificationChannel(
                    PROCESSING, "پردازش مکالمات", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("وضعیت تبدیل صدا، خلاصه‌سازی و ثبت در CRM");
            m.createNotificationChannel(channel);
        }
    }
}
