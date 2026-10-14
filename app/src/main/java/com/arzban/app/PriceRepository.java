package com.arzban.app;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public final class PriceRepository {
    public static final String ENDPOINT = "https://nerkh.jahankhahan.shop/data/live.json";

    private PriceRepository() {}

    public static Quote fetchDollar() throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Arzban-Android/1.0");
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
            long price = rates.getLong("dollar");
            if (price <= 0) throw new IllegalStateException("Invalid dollar price");

            Double change = null;
            JSONObject changes = json.optJSONObject("change");
            if (changes != null && changes.has("dollar") && !changes.isNull("dollar")) {
                double d = changes.optDouble("dollar", Double.NaN);
                if (!Double.isNaN(d)) change = d;
            }
            return new Quote(
                    price,
                    change,
                    json.optString("date", ""),
                    json.optString("time", ""),
                    System.currentTimeMillis()
            );
        } finally {
            c.disconnect();
        }
    }
}
