package org.juns.marketboardbackend.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure calculation functions over a closing-price series (oldest first). No I/O, no framework
 * dependencies — kept separate from {@link IndicatorCalculationService} so the math is directly
 * unit-testable.
 */
public final class TechnicalIndicators {

    private TechnicalIndicators() {
    }

    /** Simple moving average of the last {@code period} closes, or null if there isn't enough history yet. */
    public static BigDecimal sma(List<BigDecimal> closesOldestFirst, int period) {
        if (closesOldestFirst.size() < period) {
            return null;
        }
        List<BigDecimal> window = closesOldestFirst.subList(closesOldestFirst.size() - period, closesOldestFirst.size());
        BigDecimal sum = window.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    /**
     * RSI over {@code period} closes using a simple (not Wilder-smoothed) average of gains/losses —
     * a standard simplification that's easier to reason about and test. Null if there isn't enough
     * history yet (needs period + 1 closes to get `period` day-over-day changes).
     */
    public static BigDecimal rsi(List<BigDecimal> closesOldestFirst, int period) {
        if (closesOldestFirst.size() < period + 1) {
            return null;
        }
        List<BigDecimal> window =
                closesOldestFirst.subList(closesOldestFirst.size() - (period + 1), closesOldestFirst.size());

        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        for (int i = 1; i < window.size(); i++) {
            BigDecimal change = window.get(i).subtract(window.get(i - 1));
            if (change.signum() > 0) {
                gainSum = gainSum.add(change);
            } else {
                lossSum = lossSum.add(change.abs());
            }
        }

        BigDecimal avgGain = gainSum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        BigDecimal avgLoss = lossSum.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        if (avgLoss.signum() == 0) {
            return BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 6, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 6, RoundingMode.HALF_UP));
        return rsi.setScale(4, RoundingMode.HALF_UP);
    }
}
