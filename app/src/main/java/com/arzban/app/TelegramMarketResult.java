package com.arzban.app;

public final class TelegramMarketResult {
    public final MarketSnapshot snapshot;
    public final long fullTableMessageId;
    public final String fullTableHash;
    public final long dollarMessageId;
    public final String dollarHash;
    public final long sourcePostEpochMs;

    public TelegramMarketResult(
            MarketSnapshot snapshot,
            long fullTableMessageId,
            String fullTableHash,
            long dollarMessageId,
            String dollarHash,
            long sourcePostEpochMs
    ) {
        this.snapshot = snapshot;
        this.fullTableMessageId = fullTableMessageId;
        this.fullTableHash = fullTableHash == null ? "" : fullTableHash;
        this.dollarMessageId = dollarMessageId;
        this.dollarHash = dollarHash == null ? "" : dollarHash;
        this.sourcePostEpochMs = sourcePostEpochMs;
    }
}
