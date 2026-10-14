package com.arzban.app;

public final class AssetQuote {
    public final Asset asset;
    public final double value;
    public final Double dailyChangePercent;
    public final String serverDate;
    public final String serverTime;
    public final long fetchedAtEpochMs;

    public AssetQuote(
            Asset asset,
            double value,
            Double dailyChangePercent,
            String serverDate,
            String serverTime,
            long fetchedAtEpochMs
    ) {
        this.asset = asset;
        this.value = value;
        this.dailyChangePercent = dailyChangePercent;
        this.serverDate = serverDate == null ? "" : serverDate;
        this.serverTime = serverTime == null ? "" : serverTime;
        this.fetchedAtEpochMs = fetchedAtEpochMs;
    }
}
