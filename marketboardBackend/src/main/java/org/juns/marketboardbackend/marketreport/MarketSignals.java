package org.juns.marketboardbackend.marketreport;

import java.util.*;
import org.juns.marketboardbackend.collector.FearGreedResponse;
import org.juns.marketboardbackend.collector.MarketRiskState;
import org.juns.marketboardbackend.collector.SectorPerformance;
import org.juns.marketboardbackend.marketbreadth.dto.MarketBreadthResponse;
import org.juns.marketboardbackend.marketreport.MarketReportService.BreadthPoint;
import org.juns.marketboardbackend.marketreport.MarketReportService.Signal;
import org.juns.marketboardbackend.marketreport.MarketReportService.Trend;
import org.juns.marketboardbackend.sentiment.PutCallRatioPoint;
import org.juns.marketboardbackend.sentiment.PutCallRatioTrendResponse;

/**
 * Classifies the report's captured data into a short "market state" summary. Rules are deliberately
 * simple and spelled out in each signal's basis text; only the volatility risk state has validated
 * predictive evidence (docs/Risk_Signal_Results.md). A signal whose inputs are missing is omitted.
 */
final class MarketSignals {
    static final String POSITIVE = "POSITIVE", NEUTRAL = "NEUTRAL", CAUTION = "CAUTION";
    private static final List<String> CYCLICAL = List.of("XLK", "XLY", "XLF", "XLI");
    private static final List<String> DEFENSIVE = List.of("XLU", "XLP", "XLV");
    private static final int SLOPE_LOOKBACK = 10;

    private MarketSignals() {}

    static List<Signal> evaluate(Trend spx, MarketBreadthResponse breadth, List<BreadthPoint> breadthHistory,
            MarketRiskState risk, List<SectorPerformance> sectors, FearGreedResponse fear, PutCallRatioTrendResponse putCall) {
        List<Signal> signals = new ArrayList<>();
        Optional.ofNullable(trend(spx)).ifPresent(signals::add);
        Optional.ofNullable(breadth(breadth, breadthHistory)).ifPresent(signals::add);
        Optional.ofNullable(risk(risk)).ifPresent(signals::add);
        Optional.ofNullable(rotation(sectors)).ifPresent(signals::add);
        Optional.ofNullable(sentiment(fear, putCall)).ifPresent(signals::add);
        return List.copyOf(signals);
    }

    static Signal trend(Trend spx) {
        if (spx == null || spx.points().size() <= SLOPE_LOOKBACK) return null;
        var last = spx.points().get(spx.points().size() - 1);
        var earlier = spx.points().get(spx.points().size() - 1 - SLOPE_LOOKBACK);
        if (last.sma50() == null || earlier.sma50() == null) return null;
        double gap = (last.close() / last.sma50() - 1) * 100;
        boolean rising = last.sma50() > earlier.sma50();
        String tone = gap > 0 && rising ? POSITIVE : gap < 0 && !rising ? CAUTION : NEUTRAL;
        String value = String.format(Locale.ROOT, "S&P 500이 50일선 %s %.1f%% · 50일선 %s",
                gap >= 0 ? "위" : "아래", Math.abs(gap), rising ? "상승 중" : "하락 중");
        return new Signal("trend", "추세", tone, value,
                "종가와 50거래일 이동평균의 거리, 이동평균은 10거래일 전과 비교했습니다. 둘 다 위쪽이면 우호, 둘 다 아래쪽이면 경계입니다.", "indices");
    }

    static Signal breadth(MarketBreadthResponse today, List<BreadthPoint> history) {
        if (today == null || today.advancingCount() + today.decliningCount() == 0) return null;
        double ratio = advanceRatio(today.advancingCount(), today.decliningCount());
        var recent = history.subList(Math.max(0, history.size() - 10), history.size()).stream()
                .filter(p -> p.advancing() + p.declining() > 0).toList();
        Double average = recent.size() >= 5 ? recent.stream().mapToDouble(p -> advanceRatio(p.advancing(), p.declining())).average().orElse(ratio) : null;
        double basis = average != null ? average : ratio;
        int netHighs = today.new52wHighCount() - today.new52wLowCount();
        String tone = basis >= 55 && netHighs > 0 ? POSITIVE : basis <= 45 && netHighs < 0 ? CAUTION : NEUTRAL;
        String value = String.format(Locale.ROOT, "상승 비율 %.0f%%%s · 신고가−신저가 %+d", ratio,
                average != null ? String.format(Locale.ROOT, " (%d거래일 평균 %.0f%%)", recent.size(), average) : "", netHighs);
        return new Signal("breadth", "시장 폭", tone, value,
                "상승÷(상승+하락) 종목 비율의 최근 평균(자료가 5거래일 미만이면 당일 값)이 55% 이상이고 신고가가 신저가보다 많으면 우호, 45% 이하이고 신저가가 많으면 경계입니다.", "breadth");
    }

    static Signal risk(MarketRiskState state) {
        if (state == null || state.assets() == null) return null;
        var spy = state.assets().stream().filter(a -> "SPY".equals(a.ticker())).findFirst().orElse(null);
        if (spy == null || spy.state() == null || "UNAVAILABLE".equals(spy.state())) return null;
        long risky = state.assets().stream().filter(a -> "RISK".equals(a.state())).count();
        long caution = state.assets().stream().filter(a -> "CAUTION".equals(a.state())).count();
        String tone = "RISK".equals(spy.state()) || risky >= 5 ? CAUTION
                : "CAUTION".equals(spy.state()) || risky > 0 ? NEUTRAL : POSITIVE;
        String value = String.format(Locale.ROOT, "SPY %s%s · %d개 ETF 중 주의 %d · 위험 %d", label(spy.state()),
                spy.percentile() == null ? "" : String.format(Locale.ROOT, "(과거 대비 %.0f백분위)", Math.floor(spy.percentile())),
                state.assets().size(), caution, risky);
        return new Signal("risk", "변동성 위험", tone, value,
                "20일 변동성의 자산별 과거 백분위입니다. SPY가 위험이거나 위험 자산이 5개 이상이면 경계, SPY 주의 또는 위험 자산이 있으면 중립입니다. 홀드아웃 구간에서 이후 변동성과의 관계를 확인한 지표입니다.", "risk");
    }

    static Signal rotation(List<SectorPerformance> sectors) {
        Map<String, Double> month = new HashMap<>();
        for (var s : sectors) if (s.changePct1m() != null && Double.isFinite(s.changePct1m())) month.put(s.slug(), s.changePct1m());
        if (!month.keySet().containsAll(CYCLICAL) || !month.keySet().containsAll(DEFENSIVE)) return null;
        double cyclical = CYCLICAL.stream().mapToDouble(month::get).average().orElseThrow();
        double defensive = DEFENSIVE.stream().mapToDouble(month::get).average().orElseThrow();
        double spread = cyclical - defensive;
        String tone = spread >= 2 ? POSITIVE : spread <= -2 ? CAUTION : NEUTRAL;
        String leader = spread >= 2 ? "경기민감 섹터 주도" : spread <= -2 ? "방어 섹터 주도" : "뚜렷한 주도 없음";
        String value = String.format(Locale.ROOT, "%s · 1개월 수익률 차이 %+.1f%%p", leader, spread);
        return new Signal("rotation", "섹터 흐름", tone, value,
                "경기민감(XLK·XLY·XLF·XLI) 평균 1개월 수익률에서 방어(XLU·XLP·XLV) 평균을 뺀 값입니다. +2%p 이상이면 우호, −2%p 이하면 경계입니다.", "sectors");
    }

    static Signal sentiment(FearGreedResponse fear, PutCallRatioTrendResponse putCall) {
        if (fear == null && putCall == null) return null;
        List<String> parts = new ArrayList<>();
        boolean extreme = false;
        if (fear != null && Double.isFinite(fear.score())) {
            extreme = fear.score() <= 25 || fear.score() >= 75;
            parts.add(String.format(Locale.ROOT, "공포·탐욕 %.0f%s", fear.score(), fear.rating() == null ? "" : " (" + fear.rating() + ")"));
        }
        if (putCall != null && Double.isFinite(putCall.putCallRatio())) {
            Double rank = percentile(putCall.history(), putCall.putCallRatio());
            parts.add(String.format(Locale.ROOT, "SPY Put/Call %.2f%s", putCall.putCallRatio(),
                    rank == null ? "" : String.format(Locale.ROOT, " (보관 이력 %.0f백분위)", rank)));
            if (rank != null && (rank >= 90 || rank <= 10)) extreme = true;
        }
        if (parts.isEmpty()) return null;
        return new Signal("sentiment", "투자 심리", extreme ? CAUTION : NEUTRAL, String.join(" · ", parts),
                "공포·탐욕 25 이하·75 이상, 또는 Put/Call이 보관 이력의 상·하위 10%면 심리가 한쪽으로 쏠린 상태로 보고 경계로 표시합니다. 방향(매수·매도)을 뜻하지 않습니다.", "sentiment");
    }

    private static double advanceRatio(int advancing, int declining) {
        return advancing * 100.0 / (advancing + declining);
    }

    private static Double percentile(List<PutCallRatioPoint> history, double value) {
        if (history == null || history.size() < 10) return null;
        long below = history.stream().filter(p -> p.ratio() <= value).count();
        return below * 100.0 / history.size();
    }

    private static String label(String state) {
        return switch (state) { case "RISK" -> "위험"; case "CAUTION" -> "주의"; default -> "정상"; };
    }
}
