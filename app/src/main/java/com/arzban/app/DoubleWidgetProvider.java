package com.arzban.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public class DoubleWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) ArzbanWidgetProvider.updateOne(context, manager, id, 2);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager manager,
            int appWidgetId,
            Bundle newOptions
    ) {
        ArzbanWidgetProvider.updateOne(context, manager, appWidgetId, 2);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetPrefs.delete(context, id);
    }
}
