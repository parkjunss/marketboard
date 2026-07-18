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

    private static List<BigDecimal> closesOf(int... values) {
        return IntStream.of(values).mapToObj(BigDecimal::valueOf).toList();
    }
}
