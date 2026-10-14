package com.arzban.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PriceUpdateService extends Service {
    public static final String ACTION_START = "com.arzban.app.START";
    public static final String ACTION_STOP = "com.arzban.app.STOP";
    public static final String ACTION_REFRESH_ONCE = "com.arzban.app.REFRESH_ONCE";
    public static final String ACTION_PRICE_UPDATED = "com.arzban.app.PRICE_UPDATED";

    private static final String CHANNEL_ID = "live_price";
    private static final int NOTIFICATION_ID = 1001;
    private static final long REFRESH_SECONDS = 60L;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> periodicTask;
    private final AtomicBoolean fetchInFlight = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ArzbanUpdater");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            PriceStore.setAutoEnabled(this, false);
            stopUpdatesAndSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_REFRESH_ONCE.equals(action)) {
            startAsForeground();
            boolean shouldRemain = PriceStore.isAutoEnabled(this);
            fetchNow(!shouldRemain);
            if (shouldRemain) ensurePeriodicUpdates();
            return shouldRemain ? START_STICKY : START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            PriceStore.setAutoEnabled(this, true);
            startAsForeground();
            ensurePeriodicUpdates();
            return START_STICKY;
        }

        if (PriceStore.isAutoEnabled(this)) {
            startAsForeground();
            ensurePeriodicUpdates();
            return START_STICKY;
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    private void ensurePeriodicUpdates() {
        if (periodicTask != null && !periodicTask.isCancelled() && !periodicTask.isDone()) return;
        periodicTask = executor.scheduleWithFixedDelay(
                () -> fetchNow(false),
                0L,
                REFRESH_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void fetchNow(boolean stopAfter) {
        if (executor == null) return;
        executor.execute(() -> {
            if (!fetchInFlight.compareAndSet(false, true)) return;
            try {
                Quote quote = PriceRepository.fetchDollar();
                PriceStore.saveQuote(this, quote);
                ArzbanWidgetProvider.updateAll(this);
                updateNotification(quote);
                sendBroadcast(new Intent(ACTION_PRICE_UPDATED).setPackage(getPackageName()));
            } catch (Exception e) {
                PriceStore.setLastError(this, e.getMessage() == null ? "خطا در دریافت قیمت" : e.getMessage());
                sendBroadcast(new Intent(ACTION_PRICE_UPDATED).setPackage(getPackageName()));
            } finally {
                fetchInFlight.set(false);
                if (stopAfter) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                }
            }
        });
    }

    private void startAsForeground() {
        Notification n = buildNotification(PriceStore.loadQuote(this));
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void updateNotification(Quote q) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(q));
    }

    private Notification buildNotification(Quote q) {
        PendingIntent open = PendingIntent.getActivity(
                this, 1, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, PriceUpdateService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String content = q == null
                ? "در حال دریافت قیمت دلار…"
                : PriceFormat.toman(q.priceToman) + " تومان • هر ۶۰ ثانیه";

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ارزبان")
                .setContentText(content)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_notification, "توقف", stop
                ).build())
                .build();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "بروزرسانی زنده دلار",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("بروزرسانی ۶۰ ثانیه‌ای ویجت ارزبان");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void stopUpdatesAndSelf() {
        if (periodicTask != null) periodicTask.cancel(false);
        periodicTask = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (periodicTask != null) periodicTask.cancel(false);
        periodicTask = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
