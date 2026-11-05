package org.juns.marketboardbackend.marketreport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.juns.marketboardbackend.collector.FearGreedResponse;
import org.juns.marketboardbackend.collector.MarketRiskState;
import org.juns.marketboardbackend.collector.SectorPerformance;
import org.juns.marketboardbackend.marketbreadth.dto.MarketBreadthResponse;
import org.juns.marketboardbackend.marketreport.MarketReportService.BreadthPoint;
import org.juns.marketboardbackend.marketreport.MarketReportService.Trend;
import org.juns.marketboardbackend.marketreport.MarketReportService.TrendPoint;
import org.junit.jupiter.api.Test;

class MarketSignalsTest {

    static Trend trend(double startSma, double smaStep, double lastClose) {
        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) points.add(new TrendPoint("d" + i, startSma + smaStep * i, startSma + smaStep * i));
        var last = points.remove(points.size() - 1);
        points.add(new TrendPoint(last.tradingDate(), lastClose, last.sma50()));
        return new Trend("SPX", "S&P 500", points);
    }

    static MarketBreadthResponse breadth(int up, int down, int highs, int lows) {
        return new MarketBreadthResponse(LocalDate.now(), up, down, 0, highs, lows, up + down, Instant.now(),
                LocalDate.now(), "test", "XNYS", "v", Instant.now(), up + down, "READY");
    }

    static SectorPerformance sector(String slug, double month) {
        return new SectorPerformance(slug, slug, 0.0, 0.0, month, "2026-03-09", "2026-03-09", "t", "s", "XNYS", "READY", "a");
    }

    static MarketRiskState.Asset asset(String ticker, String state, double percentile) {
        return new MarketRiskState.Asset(ticker, ticker, "SECTOR", "READY", state, 10.0, percentile, 9.0, -1.0, 2.0, "2010-01-01", "2026-03-09");
    }

    @Test void trendNeedsBothPriceAndAverageOnTheSameSide() {
        assertThat(MarketSignals.trend(trend(100, 1, 125)).tone()).isEqualTo(MarketSignals.POSITIVE);
        assertThat(MarketSignals.trend(trend(100, -1, 70)).tone()).isEqualTo(MarketSignals.CAUTION);
        assertThat(MarketSignals.trend(trend(100, 1, 100)).tone()).isEqualTo(MarketSignals.NEUTRAL);
        assertThat(MarketSignals.trend(new Trend("SPX", "S&P 500", List.of(new TrendPoint("d", 1, null))))).isNull();
    }

    @Test void breadthUsesRecentAverageOnceFiveSessionsExist() {
        var weak = List.of(new BreadthPoint(LocalDate.now(), 100, 400, 0, 1, 1), new BreadthPoint(LocalDate.now(), 100, 400, 0, 1, 1),
                new BreadthPoint(LocalDate.now(), 100, 400, 0, 1, 1), new BreadthPoint(LocalDate.now(), 100, 400, 0, 1, 1),
                new BreadthPoint(LocalDate.now(), 400, 100, 0, 1, 1));
        // One strong day does not override a weak five-session average.
        assertThat(MarketSignals.breadth(breadth(400, 100, 30, 5), weak).tone()).isEqualTo(MarketSignals.NEUTRAL);
        assertThat(MarketSignals.breadth(breadth(400, 100, 30, 5), List.of()).tone()).isEqualTo(MarketSignals.POSITIVE);
        assertThat(MarketSignals.breadth(breadth(100, 400, 2, 40), List.of()).tone()).isEqualTo(MarketSignals.CAUTION);
    }

    @Test void riskEscalatesWithSpyStateAndRiskCount() {
        var calm = new MarketRiskState("2026-03-09", "t", "s", "a", null, Map.of(), Map.of(), List.of(asset("SPY", "NORMAL", 40.9), asset("XLK", "NORMAL", 10)));
        var signal = MarketSignals.risk(calm);
        assertThat(signal.tone()).isEqualTo(MarketSignals.POSITIVE);
        assertThat(signal.value()).contains("40백분위");
        var stressed = new MarketRiskState("2026-03-09", "t", "s", "a", null, Map.of(), Map.of(), List.of(asset("SPY", "RISK", 95), asset("XLK", "NORMAL", 10)));
        assertThat(MarketSignals.risk(stressed).tone()).isEqualTo(MarketSignals.CAUTION);
    }

    @Test void rotationComparesCyclicalAndDefensiveMonthlyReturns() {
        var riskOn = List.of(sector("XLK", 6), sector("XLY", 5), sector("XLF", 4), sector("XLI", 5), sector("XLU", 1), sector("XLP", 0), sector("XLV", 1));
        assertThat(MarketSignals.rotation(riskOn).tone()).isEqualTo(MarketSignals.POSITIVE);
        assertThat(MarketSignals.rotation(List.of(sector("XLK", 6)))).isNull();
    }

    @Test void sentimentFlagsOnlyExtremesAndNeverDirection() {
        var fear = new FearGreedResponse(18, "extreme fear", "t", Map.of(), Map.of());
        assertThat(MarketSignals.sentiment(fear, null).tone()).isEqualTo(MarketSignals.CAUTION);
        assertThat(MarketSignals.sentiment(new FearGreedResponse(50, "neutral", "t", Map.of(), Map.of()), null).tone()).isEqualTo(MarketSignals.NEUTRAL);
        assertThat(MarketSignals.sentiment(null, null)).isNull();
    }
}
