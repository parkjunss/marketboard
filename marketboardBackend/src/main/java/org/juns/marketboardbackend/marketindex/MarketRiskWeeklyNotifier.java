package org.juns.marketboardbackend.marketindex;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import org.juns.marketboardbackend.collector.MarketRiskState;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly inbox summary of the volatility risk state for users who opted into "important info".
 * Sent every week, not only on change: it is a check-in, and "nothing elevated" is also an answer.
 * Deduplicated per trading date so a repeated run (or an unchanged snapshot) never double-posts.
 */
@Component
public class MarketRiskWeeklyNotifier {
    private static final Logger log = LoggerFactory.getLogger(MarketRiskWeeklyNotifier.class);
    private static final Map<String, String> LABELS = Map.of("NORMAL", "정상", "CAUTION", "주의", "RISK", "위험");

    private final MarketRiskStateService riskState;
    private final NotificationService notifications;

    public MarketRiskWeeklyNotifier(MarketRiskStateService riskState, NotificationService notifications) {
        this.riskState = riskState;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${market-risk.weekly-cron}", zone = "Asia/Seoul")
    public void send() {
        MarketRiskState state;
        try {
            state = riskState.getLatest();
        } catch (ResourceNotFoundException ex) {
            log.warn("Weekly market risk check skipped: no risk snapshot yet");
            return;
        }
        notifications.createImportantInfoForEnabled("market-risk-weekly:" + state.expectedTradingDate(),
                title(state), message(state), "/market");
    }

    static String title(MarketRiskState state) {
        return state.assets().stream().filter(asset -> "SPY".equals(asset.ticker())).findFirst()
                .map(spy -> "주간 시장 위험 점검 · S&P 500 " + label(spy.state()))
                .orElse("주간 시장 위험 점검");
    }

    static String message(MarketRiskState state) {
        Map<String, Integer> counts = state.counts();
        String elevated = state.assets().stream()
                .filter(asset -> !"NORMAL".equals(asset.state()) && asset.percentile() != null)
                .sorted(Comparator.comparing(MarketRiskState.Asset::percentile).reversed())
                // Floor like the market page: 69.6 must not read as 70 (the caution cut-off).
                .map(asset -> asset.name() + "(" + asset.ticker() + ") " + (int) Math.floor(asset.percentile()))
                .collect(Collectors.joining(", "));
        return "기준 거래일 " + state.expectedTradingDate() + " · " + state.assets().size() + "개 ETF 중 정상 "
                + counts.getOrDefault("NORMAL", 0) + " · 주의 " + counts.getOrDefault("CAUTION", 0)
                + " · 위험 " + counts.getOrDefault("RISK", 0) + ". "
                + (elevated.isEmpty() ? "주의 이상인 자산이 없습니다. " : "주의 이상(과거 대비 백분위): " + elevated + ". ")
                + "흔들림의 크기만 알려주며 오를지 내릴지는 예측하지 않습니다.";
    }

    private static String label(String state) {
        return LABELS.getOrDefault(state, state);
    }
}
