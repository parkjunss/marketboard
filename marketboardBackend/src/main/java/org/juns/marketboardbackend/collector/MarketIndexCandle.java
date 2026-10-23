package org.juns.marketboardbackend.collector;

public record MarketIndexCandle(String ts, double open, double high, double low, double close, double volume,
        String tradingDate, String fetchedAt, String source, String sourceSymbol, String calendar,
        String sessionStatus, String adjustment) {
    public MarketIndexCandle {
        if (sessionStatus == null) sessionStatus = "UNVERIFIED";
    }
    public MarketIndexCandle(String ts, double open, double high, double low, double close, double volume) {
        this(ts, open, high, low, close, volume, null, null, null, null, null, null, null);
    }
}
