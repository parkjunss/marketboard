package org.juns.marketboardbackend.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TechnicalIndicatorsTest {

    @Test
    void smaReturnsNullWhenNotEnoughHistory() {
        List<BigDecimal> closes = closesOf(1, 2, 3);

        assertThat(TechnicalIndicators.sma(closes, 5)).isNull();
    }

    @Test
    void smaAveragesTheLastNCloses() {
        // last 3 of [10, 20, 30, 40, 50] = [30, 40, 50] -> avg 40
        List<BigDecimal> closes = closesOf(10, 20, 30, 40, 50);

        assertThat(TechnicalIndicators.sma(closes, 3)).isEqualByComparingTo("40.0000");
    }

    @Test
    void rsiReturnsNullWhenNotEnoughHistory() {
        List<BigDecimal> closes = closesOf(1, 2, 3);

        assertThat(TechnicalIndicators.rsi(closes, 5)).isNull();
    }

    @Test
    void rsiIsMaximumWhenEveryChangeIsAGain() {
        // 15 strictly increasing closes -> 14 up-moves, zero down-moves
        List<BigDecimal> closes = closesOf(IntStream.rangeClosed(1, 15).toArray());

        assertThat(TechnicalIndicators.rsi(closes, 14)).isEqualByComparingTo("100.0000");
    }

    @Test
    void rsiIsMinimumWhenEveryChangeIsALoss() {
        // 15 strictly decreasing closes -> 14 down-moves, zero up-moves
        List<BigDecimal> closes = closesOf(IntStream.rangeClosed(1, 15).map(i -> 16 - i).toArray());

        assertThat(TechnicalIndicators.rsi(closes, 14)).isEqualByComparingTo("0.0000");
    }

    @Test
    void rsiIsMidpointWhenGainsAndLossesAreEqual() {
        // alternating +1/-1 across 14 moves -> equal average gain and loss -> RSI 50
        List<BigDecimal> closes = closesOf(10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10, 11, 10);

        assertThat(TechnicalIndicators.rsi(closes, 14)).isEqualByComparingTo("50.0000");
    }

    @Test
    void rsiIsMidpointWhenPriceNeverChanges() {
        assertThat(TechnicalIndicators.rsi(closesOf(10, 10, 10, 10), 3)).isEqualByComparingTo("50.0000");
    }

    @Test
    void rsiUsesWilderSmoothingAfterTheInitialWindow() {
        assertThat(TechnicalIndicators.rsi(closesOf(10, 11, 10, 12, 11), 3)).isEqualByComparingTo("54.5455");
    }

    @Test
    void emaSeedsFromTheFirstWindowSma() {
        assertThat(TechnicalIndicators.ema(closesOf(1, 2, 3, 4), 3)).isEqualByComparingTo("3.0000");
    }

    @Test
    void macdReturnsLineSignalAndHistogram() {
        var macd = TechnicalIndicators.macd(closesOf(1, 2, 3, 4, 5, 6), 2, 3, 2);

        assertThat(macd).isNotNull();
        assertThat(macd.line()).isEqualByComparingTo("0.5000");
        assertThat(macd.signal()).isEqualByComparingTo("0.5000");
        assertThat(macd.histogram()).isEqualByComparingTo("0.0000");
    }

    @Test
    void bollingerUsesSampleStandardDeviation() {
        var bands = TechnicalIndicators.bollinger(closesOf(1, 2, 3), 3, BigDecimal.valueOf(2));

        assertThat(bands).isNotNull();
        assertThat(bands.middle()).isEqualByComparingTo("2.0000");
        assertThat(bands.upper()).isEqualByComparingTo("4.0000");
        assertThat(bands.lower()).isEqualByComparingTo("0.0000");
        assertThat(bands.percentB()).isEqualByComparingTo("0.7500");
    }

    @Test
    void atrIncludesGapsFromThePreviousClose() {
        var candles = List.of(
                new TechnicalIndicators.Ohlcv(BigDecimal.TEN, BigDecimal.valueOf(9), BigDecimal.valueOf(9), 100L),
                new TechnicalIndicators.Ohlcv(BigDecimal.valueOf(13), BigDecimal.valueOf(11), BigDecimal.valueOf(12), 150L),
                new TechnicalIndicators.Ohlcv(BigDecimal.valueOf(14), BigDecimal.valueOf(12), BigDecimal.valueOf(13), 200L));

        assertThat(TechnicalIndicators.atr(candles, 2)).isEqualByComparingTo("2.2500");
        assertThat(TechnicalIndicators.atrPercent(candles, 2)).isEqualByComparingTo("17.3077");
    }

    @Test
    void relativeVolumeExcludesTheCurrentCandleFromItsAverage() {
        var candles = List.of(
                new TechnicalIndicators.Ohlcv(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 100L),
                new TechnicalIndicators.Ohlcv(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 200L),
                new TechnicalIndicators.Ohlcv(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 450L));

        assertThat(TechnicalIndicators.relativeVolume(candles, 2)).isEqualByComparingTo("3.0000");
    }

    private static List<BigDecimal> closesOf(int... values) {
        return IntStream.of(values).mapToObj(BigDecimal::valueOf).toList();
    }
}
