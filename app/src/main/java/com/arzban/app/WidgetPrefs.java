package com.arzban.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class WidgetPrefs {
    private static final String PREFS = "arzban_widgets";

    private WidgetPrefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int widgetId, String suffix) {
        return "widget_" + widgetId + "_" + suffix;
    }

    public static void save(Context c, int widgetId, int count, Asset[] assets) {
        SharedPreferences.Editor e = prefs(c).edit()
                .putInt(key(widgetId, "count"), count);
        for (int i = 0; i < count && i < assets.length; i++) {
            e.putString(key(widgetId, "asset_" + i), assets[i].key);
        }
        e.apply();
    }

    public static int count(Context c, int widgetId, int fallback) {
        return prefs(c).getInt(key(widgetId, "count"), fallback);
    }

    public static Asset[] assets(Context c, int widgetId, int count) {
        Asset[] defaults = new Asset[]{Asset.DOLLAR, Asset.GOLD18, Asset.COIN_EMAMI};
        Asset[] result = new Asset[count];
        SharedPreferences p = prefs(c);

        for (int i = 0; i < count; i++) {
            Asset selected = Asset.fromKey(
                    p.getString(key(widgetId, "asset_" + i), defaults[i].key)
            );
            result[i] = selected.sourceSupported ? selected : defaults[i];
        }
        return result;
    }

    public static void delete(Context c, int widgetId) {
        SharedPreferences.Editor e = prefs(c).edit();
        e.remove(key(widgetId, "count"));
        e.remove(key(widgetId, "asset_0"));
        e.remove(key(widgetId, "asset_1"));
        e.remove(key(widgetId, "asset_2"));
        e.apply();
    }
}
