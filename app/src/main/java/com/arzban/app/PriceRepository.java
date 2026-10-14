package com.arzban.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public final class PriceRepository {
    public static final String ENDPOINT = "https://nerkh.jahankhahan.shop/data/live.json";

    private PriceRepository() {}

    public static MarketSnapshot fetchMarket() throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Arzban-Android/1.1");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code > 299) throw new IllegalStateException("HTTP " + code);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            JSONObject rates = json.getJSONObject("rates");
            JSONObject changes = json.optJSONObject("change");
            String date = json.optString("date", "");
            String time = json.optString("time", "");
            long fetchedAt = System.currentTimeMillis();

            Map<Asset, AssetQuote> quotes = new EnumMap<>(Asset.class);
            for (Asset asset : Asset.values()) {
                Double value = readValue(json, rates, asset);
                if (value == null || value <= 0) continue;

                Double change = null;
                if (asset != Asset.OUNCE && changes != null && changes.has(asset.key) && !changes.isNull(asset.key)) {
                    double d = changes.optDouble(asset.key, Double.NaN);
                    if (!Double.isNaN(d)) change = d;
                }

                quotes.put(asset, new AssetQuote(asset, value, change, date, time, fetchedAt));
            }

            if (!quotes.containsKey(Asset.DOLLAR)) {
                throw new IllegalStateException("Dollar price missing from market feed");
            }
            return new MarketSnapshot(quotes, date, time, fetchedAt);
        } finally {
            c.disconnect();
        }
    }

    private static Double readValue(JSONObject root, JSONObject rates, Asset asset) {
        if (asset == Asset.OUNCE) {
            JSONObject usd = root.optJSONObject("usd");
            if (usd != null) {
                double d = usd.optDouble("ounce", Double.NaN);
                if (!Double.isNaN(d)) return d;
            }
            double d = root.optDouble("ounce", Double.NaN);
            return Double.isNaN(d) ? null : d;
        }

        if (!rates.has(asset.key) || rates.isNull(asset.key)) return null;
        double d = rates.optDouble(asset.key, Double.NaN);
        return Double.isNaN(d) ? null : d;
    }
}
