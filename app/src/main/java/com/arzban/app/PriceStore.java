package com.arzban.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

public final class PriceStore {
    private static final String PREFS = "arzban_state";
    private static final String AUTO = "auto";
    private static final String ERROR = "error";
    private static final String LAST_DATE = "market_date";
    private static final String LAST_TIME = "market_time";
    private static final String LAST_FETCH = "market_fetched_at";

    private PriceStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String k(Asset asset, String suffix) {
        return "asset_" + asset.key + "_" + suffix;
    }

    public static void saveSnapshot(Context c, MarketSnapshot snapshot) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit()
                .putString(LAST_DATE, snapshot.serverDate)
                .putString(LAST_TIME, snapshot.serverTime)
                .putLong(LAST_FETCH, snapshot.fetchedAtEpochMs)
                .putString(ERROR, "");

        for (Map.Entry<Asset, AssetQuote> entry : snapshot.quotes.entrySet()) {
            AssetQuote q = entry.getValue();

            String oldRaw = p.getString(k(q.asset, "value"), null);
            if (oldRaw != null) {
                e.putString(k(q.asset, "previous_value"), oldRaw);
            }

            e.putString(k(q.asset, "value"), Double.toString(q.value));
            e.putBoolean(k(q.asset, "has_change"), q.dailyChangePercent != null);
            if (q.dailyChangePercent != null) {
                e.putString(k(q.asset, "change"), Double.toString(q.dailyChangePercent));
            }
            e.putString(k(q.asset, "date"), q.serverDate);
            e.putString(k(q.asset, "time"), q.serverTime);
            e.putLong(k(q.asset, "fetched"), q.fetchedAtEpochMs);
        }
        e.apply();
    }

    public static AssetQuote loadQuote(Context c, Asset asset) {
        SharedPreferences p = prefs(c);
        String raw = p.getString(k(asset, "value"), null);
        if (raw == null) return null;
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (Exception e) {
            return null;
        }
        if (value <= 0) return null;

        Double change = null;
        if (p.getBoolean(k(asset, "has_change"), false)) {
            try {
                change = Double.valueOf(p.getString(k(asset, "change"), "0"));
            } catch (Exception ignored) {}
        }

        return new AssetQuote(
                asset,
                value,
                change,
                p.getString(k(asset, "date"), ""),
                p.getString(k(asset, "time"), ""),
                p.getLong(k(asset, "fetched"), 0L)
        );
    }

    public static Double previousValue(Context c, Asset asset) {
        String raw = prefs(c).getString(k(asset, "previous_value"), null);
        if (raw == null) return null;
        try {
            double v = Double.parseDouble(raw);
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static long lastFetchEpochMs(Context c) {
        return prefs(c).getLong(LAST_FETCH, 0L);
    }

    public static String lastMarketDate(Context c) {
        return prefs(c).getString(LAST_DATE, "");
    }

    public static String lastMarketTime(Context c) {
        return prefs(c).getString(LAST_TIME, "");
    }

    public static boolean isMarketOpen(Context c) {
        String time = lastMarketTime(c);
        return time != null && !time.isEmpty();
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
