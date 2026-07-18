package org.juns.marketboardbackend.collector;

public record MarketIndexCandle(String ts, double open, double high, double low, double close, double volume) {
}
