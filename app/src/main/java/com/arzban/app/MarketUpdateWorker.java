package com.arzban.app;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class MarketUpdateWorker extends Worker {
    public MarketUpdateWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        PriceStore.ensureTelegramMigration(context);

        try {
            TelegramMarketResult result = PriceRepository.fetchTelegramMarket();
            boolean changed = PriceStore.isTelegramResultNew(context, result);

            if (changed) {
                PriceStore.saveSnapshot(context, result.snapshot);
                PriceStore.saveTelegramSourceState(context, result);
                ArzbanWidgetProvider.updateAll(context);
                MarketNotifier.showLatest(context);
                notifyUi(context);
            } else {
                PriceStore.markChecked(context);
            }

            return Result.success();
        } catch (Exception e) {
            PriceStore.markChecked(context);
            PriceStore.setLastError(
                    context,
                    e.getMessage() == null ? "خطا در بررسی منبع قیمت" : e.getMessage()
            );
            notifyUi(context);
            return Result.retry();
        }
    }

    private void notifyUi(Context context) {
        context.sendBroadcast(
                new Intent("com.arzban.app.PRICE_UPDATED")
                        .setPackage(context.getPackageName())
        );
    }
}
