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
    private static final String SELECTED_1 = "selected_1";
    private static final String SELECTED_2 = "selected_2";
    private static final String SELECTED_3 = "selected_3";

    private PriceStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String k(Asset asset, String suffix) {
        return "asset_" + asset.key + "_" + suffix;
    }

    public static void saveSnapshot(Context c, MarketSnapshot snapshot) {
        SharedPreferences.Editor e = prefs(c).edit()
                .putString(LAST_DATE, snapshot.serverDate)
                .putString(LAST_TIME, snapshot.serverTime)
                .putLong(LAST_FETCH, snapshot.fetchedAtEpochMs)
                .putString(ERROR, "");

        for (Map.Entry<Asset, AssetQuote> entry : snapshot.quotes.entrySet()) {
            AssetQuote q = entry.getValue();
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

    public static Asset[] getSelectedAssets(Context c) {
        SharedPreferences p = prefs(c);
        Asset a1 = Asset.fromKey(p.getString(SELECTED_1, Asset.DOLLAR.key));
        Asset a2 = Asset.fromKey(p.getString(SELECTED_2, Asset.GOLD18.key));
        Asset a3 = Asset.fromKey(p.getString(SELECTED_3, Asset.COIN_EMAMI.key));

        if (a2 == a1) a2 = Asset.GOLD18;
        if (a3 == a1 || a3 == a2) a3 = Asset.COIN_EMAMI;
        return new Asset[]{a1, a2, a3};
    }

    public static boolean setSelectedAssets(Context c, Asset a1, Asset a2, Asset a3) {
        if (a1 == null || a2 == null || a3 == null) return false;
        if (a1 == a2 || a1 == a3 || a2 == a3) return false;
        prefs(c).edit()
                .putString(SELECTED_1, a1.key)
                .putString(SELECTED_2, a2.key)
                .putString(SELECTED_3, a3.key)
                .apply();
        return true;
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
