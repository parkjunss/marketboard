package org.juns.marketboardbackend.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure calculation functions over a closing-price series (oldest first). No I/O, no framework
 * dependencies — kept separate from {@link IndicatorCalculationService} so the math is directly
 * unit-testable.
 */
public final class TechnicalIndicators {

    private static final int SCALE = 4;

    public record Macd(BigDecimal line, BigDecimal signal, BigDecimal histogram) {}

    public record BollingerBands(BigDecimal upper, BigDecimal middle, BigDecimal lower, BigDecimal percentB) {}

    public record Ohlcv(BigDecimal high, BigDecimal low, BigDecimal close, Long volume) {}

    private TechnicalIndicators() {
    }

    /** Simple moving average of the last {@code period} closes, or null if there isn't enough history yet. */
    public static BigDecimal sma(List<BigDecimal> closesOldestFirst, int period) {
        if (closesOldestFirst.size() < period) {
            return null;
        }
        List<BigDecimal> window = closesOldestFirst.subList(closesOldestFirst.size() - period, closesOldestFirst.size());
        BigDecimal sum = window.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(period), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Wilder RSI over {@code period} closes. The initial gain/loss averages use the first period
     * changes; subsequent values use Wilder's recursive smoothing.
     */
    public static BigDecimal rsi(List<BigDecimal> closesOldestFirst, int period) {
        if (closesOldestFirst.size() < period + 1) {
            return null;
        }
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            BigDecimal change = closesOldestFirst.get(i).subtract(closesOldestFirst.get(i - 1));
            if (change.signum() > 0) {
                gainSum = gainSum.add(change);
            } else {
                lossSum = lossSum.add(change.abs());
            }
        }

        BigDecimal divisor = BigDecimal.valueOf(period);
        BigDecimal avgGain = gainSum.divide(divisor, 12, RoundingMode.HALF_UP);
        BigDecimal avgLoss = lossSum.divide(divisor, 12, RoundingMode.HALF_UP);
        for (int i = period + 1; i < closesOldestFirst.size(); i++) {
            BigDecimal change = closesOldestFirst.get(i).subtract(closesOldestFirst.get(i - 1));
            BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
            BigDecimal loss = change.signum() < 0 ? change.abs() : BigDecimal.ZERO;
            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1)).add(gain)
                    .divide(divisor, 12, RoundingMode.HALF_UP);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1)).add(loss)
                    .divide(divisor, 12, RoundingMode.HALF_UP);
        }
        if (avgGain.signum() == 0 && avgLoss.signum() == 0) {
            return BigDecimal.valueOf(50).setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (avgLoss.signum() == 0) {
            return BigDecimal.valueOf(100).setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal rs = avgGain.divide(avgLoss, 12, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 12, RoundingMode.HALF_UP));
        return rsi.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal ema(List<BigDecimal> closesOldestFirst, int period) {
        List<BigDecimal> values = emaSeries(closesOldestFirst, period);
        return values.isEmpty() ? null : values.get(values.size() - 1).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Macd macd(List<BigDecimal> closesOldestFirst, int fastPeriod, int slowPeriod, int signalPeriod) {
        if (fastPeriod >= slowPeriod) throw new IllegalArgumentException("fastPeriod must be less than slowPeriod");
        List<BigDecimal> fast = emaSeries(closesOldestFirst, fastPeriod);
        List<BigDecimal> slow = emaSeries(closesOldestFirst, slowPeriod);
        if (slow.isEmpty()) return null;
        int offset = slowPeriod - fastPeriod;
        List<BigDecimal> lines = new ArrayList<>();
        for (int i = 0; i < slow.size(); i++) lines.add(fast.get(i + offset).subtract(slow.get(i)));
        List<BigDecimal> signals = emaSeries(lines, signalPeriod);
        if (signals.isEmpty()) return null;
        BigDecimal line = lines.get(lines.size() - 1);
        BigDecimal signal = signals.get(signals.size() - 1);
        return new Macd(scale(line), scale(signal), scale(line.subtract(signal)));
    }

    public static BollingerBands bollinger(List<BigDecimal> closesOldestFirst, int period, BigDecimal multiplier) {
        if (closesOldestFirst.size() < period || period < 2) return null;
        List<BigDecimal> window = closesOldestFirst.subList(closesOldestFirst.size() - period, closesOldestFirst.size());
        BigDecimal middle = window.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 12, RoundingMode.HALF_UP);
        double variance = window.stream().mapToDouble(v -> Math.pow(v.doubleValue() - middle.doubleValue(), 2)).sum()
                / (period - 1);
        BigDecimal distance = BigDecimal.valueOf(Math.sqrt(variance)).multiply(multiplier);
        BigDecimal upper = middle.add(distance);
        BigDecimal lower = middle.subtract(distance);
        BigDecimal width = upper.subtract(lower);
        BigDecimal percentB = width.signum() == 0 ? null : closesOldestFirst.get(closesOldestFirst.size() - 1)
                .subtract(lower).divide(width, 12, RoundingMode.HALF_UP);
        return new BollingerBands(scale(upper), scale(middle), scale(lower), percentB == null ? null : scale(percentB));
    }

    public static BigDecimal atr(List<Ohlcv> candlesOldestFirst, int period) {
        if (candlesOldestFirst.size() < period || period < 1) return null;
        List<BigDecimal> trueRanges = new ArrayList<>();
        for (int i = 0; i < candlesOldestFirst.size(); i++) {
            Ohlcv current = candlesOldestFirst.get(i);
            BigDecimal range = current.high().subtract(current.low()).abs();
            if (i > 0) {
                BigDecimal previousClose = candlesOldestFirst.get(i - 1).close();
                range = range.max(current.high().subtract(previousClose).abs())
                        .max(current.low().subtract(previousClose).abs());
            }
            trueRanges.add(range);
        }
        BigDecimal divisor = BigDecimal.valueOf(period);
        BigDecimal value = trueRanges.subList(0, period).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(divisor, 12, RoundingMode.HALF_UP);
        for (int i = period; i < trueRanges.size(); i++) {
            value = value.multiply(BigDecimal.valueOf(period - 1)).add(trueRanges.get(i))
                    .divide(divisor, 12, RoundingMode.HALF_UP);
        }
        return scale(value);
    }

    public static BigDecimal atrPercent(List<Ohlcv> candlesOldestFirst, int period) {
        BigDecimal value = atr(candlesOldestFirst, period);
        if (value == null || candlesOldestFirst.get(candlesOldestFirst.size() - 1).close().signum() == 0) return null;
        return scale(value.multiply(BigDecimal.valueOf(100))
                .divide(candlesOldestFirst.get(candlesOldestFirst.size() - 1).close(), 12, RoundingMode.HALF_UP));
    }

    public static BigDecimal relativeVolume(List<Ohlcv> candlesOldestFirst, int period) {
        if (candlesOldestFirst.size() < period + 1 || period < 1) return null;
        int currentIndex = candlesOldestFirst.size() - 1;
        BigDecimal average = candlesOldestFirst.subList(currentIndex - period, currentIndex).stream()
                .map(candle -> BigDecimal.valueOf(candle.volume()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 12, RoundingMode.HALF_UP);
        if (average.signum() == 0) return null;
        return scale(BigDecimal.valueOf(candlesOldestFirst.get(currentIndex).volume())
                .divide(average, 12, RoundingMode.HALF_UP));
    }

    private static List<BigDecimal> emaSeries(List<BigDecimal> values, int period) {
        if (period < 1) throw new IllegalArgumentException("period must be positive");
        if (values.size() < period) return List.of();
        BigDecimal alpha = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1L), 12, RoundingMode.HALF_UP);
        BigDecimal current = values.subList(0, period).stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 12, RoundingMode.HALF_UP);
        List<BigDecimal> result = new ArrayList<>();
        result.add(current);
        for (int i = period; i < values.size(); i++) {
            current = values.get(i).multiply(alpha).add(current.multiply(BigDecimal.ONE.subtract(alpha)));
            result.add(current);
        }
        return result;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
