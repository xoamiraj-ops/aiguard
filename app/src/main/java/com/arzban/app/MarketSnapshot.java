package com.arzban.app;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class MarketSnapshot {
    public final Map<Asset, AssetQuote> quotes;
    public final String serverDate;
    public final String serverTime;
    public final long fetchedAtEpochMs;

    public MarketSnapshot(
            Map<Asset, AssetQuote> quotes,
            String serverDate,
            String serverTime,
            long fetchedAtEpochMs
    ) {
        this.quotes = Collections.unmodifiableMap(new EnumMap<>(quotes));
        this.serverDate = serverDate == null ? "" : serverDate;
        this.serverTime = serverTime == null ? "" : serverTime;
        this.fetchedAtEpochMs = fetchedAtEpochMs;
    }
}
