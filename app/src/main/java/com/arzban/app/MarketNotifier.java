package com.arzban.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Locale;

public final class MarketNotifier {
    private static final String CHANNEL_ID = "market_updates";
    private static final int NOTIFICATION_ID = 1001;

    private MarketNotifier() {}

    public static void showLatest(Context context) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        createChannel(context);

        Asset asset = Asset.DOLLAR;
        AssetQuote quote = PriceStore.loadQuote(context, asset);
        if (quote == null) return;

        String trend = trendText(context, asset, quote.value);
        String content = asset.titleFa + " "
                + PriceFormat.value(asset, quote.value) + " "
                + asset.unitLabel() + "  " + trend;

        PendingIntent open = PendingIntent.getActivity(
                context,
                1,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ارزبان • @DO_L4")
                .setContentText(content)
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();

        context.getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, notification);
    }

    private static String trendText(Context context, Asset asset, double current) {
        Double previous = PriceStore.previousValue(context, asset);
        if (previous == null || previous <= 0) return "⚪ ●";

        double pct = ((current - previous) / previous) * 100.0;
        if (pct > 0.0001) {
            return "🟢 ▲ " + String.format(Locale.US, "+%.2f%%", pct);
        }
        if (pct < -0.0001) {
            return "🔴 ▼ " + String.format(Locale.US, "%.2f%%", pct);
        }
        return "⚪ ● 0.00%";
    }

    private static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "بروزرسانی قیمت بازار",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("اعلان آخرین نرخ دریافت‌شده از منبع قیمت ارزبان");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
