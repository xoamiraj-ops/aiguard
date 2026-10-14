package com.arzban.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

public class ArzbanWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, buildViews(context));
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ArzbanWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) manager.updateAppWidget(id, buildViews(context));
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_arzban);
        Asset[] selected = PriceStore.getSelectedAssets(context);

        setRow(context, v, selected[0],
                R.id.widget_asset1_name, R.id.widget_asset1_price, R.id.widget_asset1_change);
        setRow(context, v, selected[1],
                R.id.widget_asset2_name, R.id.widget_asset2_price, R.id.widget_asset2_change);
        setRow(context, v, selected[2],
                R.id.widget_asset3_name, R.id.widget_asset3_price, R.id.widget_asset3_change);

        String time = PriceStore.lastMarketTime(context);
        if (time == null || time.isEmpty()) {
            long fetched = PriceStore.lastFetchEpochMs(context);
            time = fetched > 0 ? PriceFormat.localTime(fetched) : "—";
        }
        v.setTextViewText(R.id.widget_time, "آپدیت " + time + " • Nerkhsanj");

        PendingIntent open = PendingIntent.getActivity(
                context, 20, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        v.setOnClickPendingIntent(R.id.widget_root, open);

        Intent refreshIntent = new Intent(context, PriceUpdateService.class);
        refreshIntent.setAction(PriceUpdateService.ACTION_REFRESH_ONCE);
        PendingIntent refresh = PendingIntent.getForegroundService(
                context, 21, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        v.setOnClickPendingIntent(R.id.widget_refresh, refresh);
        return v;
    }

    private static void setRow(
            Context context,
            RemoteViews v,
            Asset asset,
            int nameId,
            int priceId,
            int changeId
    ) {
        v.setTextViewText(nameId, asset.titleFa + "  " + asset.symbol);
        AssetQuote q = PriceStore.loadQuote(context, asset);

        if (q == null) {
            v.setTextViewText(priceId, "—");
            v.setTextViewText(changeId, "—");
            v.setTextColor(changeId, Color.parseColor("#A7AE9A"));
            return;
        }

        v.setTextViewText(priceId, PriceFormat.value(asset, q.value) + " " + asset.unitLabel());
        v.setTextViewText(changeId, PriceFormat.change(q.dailyChangePercent));

        String color = "#A7AE9A";
        if (q.dailyChangePercent != null) {
            if (q.dailyChangePercent > 0) color = "#A8D08D";
            else if (q.dailyChangePercent < 0) color = "#E08078";
        }
        v.setTextColor(changeId, Color.parseColor(color));
    }
}
