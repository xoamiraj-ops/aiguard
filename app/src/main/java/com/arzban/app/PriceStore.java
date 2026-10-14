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
    private static final String LAST_CHECK = "market_last_check";
    private static final String SOURCE_VERSION = "source_version";
    private static final String SOURCE_FULL_ID = "source_full_id";
    private static final String SOURCE_FULL_HASH = "source_full_hash";
    private static final String SOURCE_DOLLAR_ID = "source_dollar_id";
    private static final String SOURCE_DOLLAR_HASH = "source_dollar_hash";

    private static final int TELEGRAM_SOURCE_VERSION = 2;

    private PriceStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String k(Asset asset, String suffix) {
        return "asset_" + asset.key + "_" + suffix;
    }

    public static void ensureTelegramMigration(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getInt(SOURCE_VERSION, 0) >= TELEGRAM_SOURCE_VERSION) return;

        SharedPreferences.Editor e = p.edit()
                .putInt(SOURCE_VERSION, TELEGRAM_SOURCE_VERSION)
                .putBoolean(AUTO, true)
                .putString(ERROR, "")
                .putLong(LAST_FETCH, 0L)
                .putLong(LAST_CHECK, 0L)
                .putString(LAST_DATE, "")
                .putString(LAST_TIME, "")
                .putLong(SOURCE_FULL_ID, 0L)
                .putString(SOURCE_FULL_HASH, "")
                .putLong(SOURCE_DOLLAR_ID, 0L)
                .putString(SOURCE_DOLLAR_HASH, "");

        // Values from the old Nerkhsanj source must never be shown as DO_L4 data.
        for (Asset asset : Asset.values()) {
            e.remove(k(asset, "value"));
            e.remove(k(asset, "previous_value"));
            e.remove(k(asset, "has_change"));
            e.remove(k(asset, "change"));
            e.remove(k(asset, "date"));
            e.remove(k(asset, "time"));
            e.remove(k(asset, "fetched"));
        }
        e.apply();
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
            } else {
                e.remove(k(q.asset, "change"));
            }
            e.putString(k(q.asset, "date"), q.serverDate);
            e.putString(k(q.asset, "time"), q.serverTime);
            e.putLong(k(q.asset, "fetched"), q.fetchedAtEpochMs);
        }
        e.apply();
    }

    public static void saveTelegramSourceState(Context c, TelegramMarketResult result) {
        prefs(c).edit()
                .putLong(SOURCE_FULL_ID, result.fullTableMessageId)
                .putString(SOURCE_FULL_HASH, result.fullTableHash)
                .putLong(SOURCE_DOLLAR_ID, result.dollarMessageId)
                .putString(SOURCE_DOLLAR_HASH, result.dollarHash)
                .putLong(LAST_CHECK, System.currentTimeMillis())
                .apply();
    }

    public static boolean isTelegramResultNew(Context c, TelegramMarketResult result) {
        SharedPreferences p = prefs(c);

        long oldFullId = p.getLong(SOURCE_FULL_ID, 0L);
        String oldFullHash = p.getString(SOURCE_FULL_HASH, "") == null
                ? "" : p.getString(SOURCE_FULL_HASH, "");

        long oldDollarId = p.getLong(SOURCE_DOLLAR_ID, 0L);
        String oldDollarHash = p.getString(SOURCE_DOLLAR_HASH, "") == null
                ? "" : p.getString(SOURCE_DOLLAR_HASH, "");

        boolean fullChanged = result.fullTableMessageId > oldFullId
                || (result.fullTableMessageId == oldFullId
                && !result.fullTableHash.equals(oldFullHash));

        boolean dollarChanged = result.dollarMessageId > oldDollarId
                || (result.dollarMessageId == oldDollarId
                && !result.dollarHash.equals(oldDollarHash));

        return fullChanged || dollarChanged || lastFetchEpochMs(c) <= 0L;
    }

    public static void markChecked(Context c) {
        prefs(c).edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply();
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

    public static long lastCheckEpochMs(Context c) {
        return prefs(c).getLong(LAST_CHECK, 0L);
    }

    public static String lastMarketDate(Context c) {
        return prefs(c).getString(LAST_DATE, "");
    }

    public static String lastMarketTime(Context c) {
        return prefs(c).getString(LAST_TIME, "");
    }

    public static boolean isSourceFresh(Context c) {
        long last = lastFetchEpochMs(c);
        if (last <= 0L) return false;
        return System.currentTimeMillis() - last < 2L * 60L * 60L * 1000L;
    }

    public static void setAutoEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(AUTO, enabled).apply();
    }

    public static boolean isAutoEnabled(Context c) {
        return prefs(c).getBoolean(AUTO, true);
    }

    public static void setLastError(Context c, String message) {
        prefs(c).edit().putString(ERROR, message == null ? "" : message).apply();
    }

    public static String lastError(Context c) {
        return prefs(c).getString(ERROR, "");
    }
}
