package com.arzban.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class SingleWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) ArzbanWidgetProvider.updateOne(context, manager, id, 1);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetPrefs.delete(context, id);
    }
}
