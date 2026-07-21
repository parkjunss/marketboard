package org.juns.marketboardbackend.collector;

public record SymbolProfileResponse(
        String ticker,
        String name,
        String exchange,
        String sector,
        String industry,
        String currency,
        Long marketCap,
        String longBusinessSummary,
        String website,
        Integer fullTimeEmployees,
        String city,
        String country,
        Double trailingPE,
        Double forwardPE,
        Double dividendYield,
        Double beta,
        Long averageVolume,
        String recommendationKey,
        Double targetMeanPrice,
        Integer numberOfAnalystOpinions) {
}
