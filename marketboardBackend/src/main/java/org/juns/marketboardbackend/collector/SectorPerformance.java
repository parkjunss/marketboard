package org.juns.marketboardbackend.collector;

public record SectorPerformance(String slug, String name, Double changePct1d, Double changePct1w, Double changePct1m) {
}
