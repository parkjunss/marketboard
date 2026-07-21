package org.juns.marketboardbackend.collector;

import java.util.Map;

public record FearGreedResponse(
        double score, String rating, String timestamp, Map<String, Double> history, Map<String, SubIndicator> indicators) {

    public record SubIndicator(double score, String rating) {
    }
}
