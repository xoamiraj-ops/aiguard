package com.arzban.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class PriceStore {
    private static final String PREFS = "arzban_state";
    private static final String PRICE = "price";
    private static final String CHANGE = "change";
    private static final String HAS_CHANGE = "has_change";
    private static final String DATE = "date";
    private static final String TIME = "time";
    private static final String FETCHED = "fetched";
    private static final String AUTO = "auto";
    private static final String ERROR = "error";

    private PriceStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveQuote(Context c, Quote q) {
        SharedPreferences.Editor e = prefs(c).edit()
                .putLong(PRICE, q.priceToman)
                .putBoolean(HAS_CHANGE, q.dailyChangePercent != null)
                .putString(DATE, q.serverDate)
                .putString(TIME, q.serverTime)
                .putLong(FETCHED, q.fetchedAtEpochMs)
                .putString(ERROR, "");
        if (q.dailyChangePercent != null) e.putString(CHANGE, q.dailyChangePercent.toString());
        e.apply();
    }

    public static Quote loadQuote(Context c) {
        SharedPreferences p = prefs(c);
        long price = p.getLong(PRICE, 0L);
        if (price <= 0) return null;
        Double change = null;
        if (p.getBoolean(HAS_CHANGE, false)) {
            try { change = Double.valueOf(p.getString(CHANGE, "0")); } catch (Exception ignored) {}
        }
        return new Quote(
                price,
                change,
                p.getString(DATE, ""),
                p.getString(TIME, ""),
                p.getLong(FETCHED, 0L)
        );
    }

    public static void setAutoEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(AUTO, enabled).apply();
    }

    public static boolean isAutoEnabled(Context c) {
        return prefs(c).getBoolean(AUTO, false);
    }

    public static void setLastError(Context c, String message) {
        prefs(c).edit().putString(ERROR, message == null ? "" : message).apply();
    }

    public static String lastError(Context c) {
        return prefs(c).getString(ERROR, "");
    }
}
