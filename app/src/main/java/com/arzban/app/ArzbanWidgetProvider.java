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
        Quote q = PriceStore.loadQuote(context);

        if (q == null) {
            v.setTextViewText(R.id.widget_price, "—");
            v.setTextViewText(R.id.widget_change, "برای بروزرسانی لمس کنید");
            v.setTextViewText(R.id.widget_time, "");
            v.setTextColor(R.id.widget_change, Color.parseColor("#A7AE9A"));
        } else {
            v.setTextViewText(R.id.widget_price, PriceFormat.toman(q.priceToman));
            v.setTextViewText(R.id.widget_change, PriceFormat.change(q.dailyChangePercent));
            String t = q.serverTime.isEmpty() ? PriceFormat.localTime(q.fetchedAtEpochMs) : q.serverTime;
            v.setTextViewText(R.id.widget_time, "آپدیت " + t + " • Nerkhsanj");

            String changeColor = "#A7AE9A";
            if (q.dailyChangePercent != null) {
                if (q.dailyChangePercent > 0) changeColor = "#A8D08D";
                else if (q.dailyChangePercent < 0) changeColor = "#E08078";
            }
            v.setTextColor(R.id.widget_change, Color.parseColor(changeColor));
        }

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
}
