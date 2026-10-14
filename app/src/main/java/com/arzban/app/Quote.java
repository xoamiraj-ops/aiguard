package com.arzban.app;

public final class Quote {
    public final long priceToman;
    public final Double dailyChangePercent;
    public final String serverDate;
    public final String serverTime;
    public final long fetchedAtEpochMs;

    public Quote(long priceToman, Double dailyChangePercent, String serverDate, String serverTime, long fetchedAtEpochMs) {
        this.priceToman = priceToman;
        this.dailyChangePercent = dailyChangePercent;
        this.serverDate = serverDate == null ? "" : serverDate;
        this.serverTime = serverTime == null ? "" : serverTime;
        this.fetchedAtEpochMs = fetchedAtEpochMs;
    }
}
