package org.juns.marketboardbackend.collector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BacktestEngineRequest(
        List<String> tickers, LocalDate startDate, LocalDate endDate, BigDecimal initialCapital, BigDecimal riskFreeRate) {
}
