package org.juns.marketboardbackend.collector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EtfMomentumResult(
        LocalDate asOf, LocalDate startDate, LocalDate endDate, boolean ready,
        List<Row> rows, BigDecimal cashWeight) {
    public record Row(String ticker, Integer rank, BigDecimal momentum, BigDecimal close,
                      BigDecimal sma200, LocalDate sessionDate, BigDecimal weight, String reason) {}
}
