package com.arzban.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

public final class ArzbanWidgetProvider {
    private ArzbanWidgetProvider() {}

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        updateComponent(context, manager, SingleWidgetProvider.class, 1);
        updateComponent(context, manager, DoubleWidgetProvider.class, 2);
        updateComponent(context, manager, TripleWidgetProvider.class, 3);
    }

    private static void updateComponent(
            Context context,
            AppWidgetManager manager,
            Class<?> providerClass,
            int count
    ) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, providerClass));
        if (ids == null) return;
        for (int id : ids) updateOne(context, manager, id, count);
    }

    public static void updateOne(
            Context context,
            AppWidgetManager manager,
            int widgetId,
            int count
    ) {
        int layout = count == 1
                ? R.layout.widget_single
                : count == 2 ? R.layout.widget_double : R.layout.widget_triple;

        RemoteViews v = new RemoteViews(context.getPackageName(), layout);
        Asset[] selected = WidgetPrefs.assets(context, widgetId, count);

        setRow(context, v, selected[0],
                R.id.widget_asset1_name, R.id.widget_asset1_price, R.id.widget_asset1_change);

        if (count >= 2) {
            setRow(context, v, selected[1],
                    R.id.widget_asset2_name, R.id.widget_asset2_price, R.id.widget_asset2_change);
        }

        if (count >= 3) {
            setRow(context, v, selected[2],
                    R.id.widget_asset3_name, R.id.widget_asset3_price, R.id.widget_asset3_change);
        }

        String time = PriceStore.lastMarketTime(context);
        if (time == null || time.isEmpty()) {
            long fetched = PriceStore.lastFetchEpochMs(context);
            time = fetched > 0 ? PriceFormat.localTime(fetched) : "—";
        }
        v.setTextViewText(R.id.widget_time, "آپدیت " + time);

        PendingIntent open = PendingIntent.getActivity(
                context,
                1000 + widgetId,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        v.setOnClickPendingIntent(R.id.widget_root, open);

        Intent refreshIntent = new Intent(context, PriceUpdateService.class);
        refreshIntent.setAction(PriceUpdateService.ACTION_REFRESH_ONCE);
        PendingIntent refresh = PendingIntent.getForegroundService(
                context,
                2000 + widgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        v.setOnClickPendingIntent(R.id.widget_refresh, refresh);

        manager.updateAppWidget(widgetId, v);
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
