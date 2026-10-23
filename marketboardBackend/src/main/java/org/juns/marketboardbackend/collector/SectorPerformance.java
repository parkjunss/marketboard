package org.juns.marketboardbackend.collector;

public record SectorPerformance(String slug, String name, Double changePct1d, Double changePct1w, Double changePct1m,
        String tradingDate, String expectedTradingDate, String fetchedAt, String source, String calendar,
        String status, String adjustment) {
    public SectorPerformance {
        if (status == null) status = "UNVERIFIED";
    }
    public SectorPerformance(String slug, String name, Double changePct1d, Double changePct1w, Double changePct1m) {
        this(slug, name, changePct1d, changePct1w, changePct1m, null, null, null, null, null, null, null);
    }
}
