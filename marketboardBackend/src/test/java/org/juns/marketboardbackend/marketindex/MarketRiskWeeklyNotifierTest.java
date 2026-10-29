package org.juns.marketboardbackend.marketindex;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.juns.marketboardbackend.collector.MarketRiskState;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketRiskWeeklyNotifierTest {
    @Mock MarketRiskStateService riskState;
    @Mock NotificationService notifications;

    private static MarketRiskState.Asset asset(String ticker, String name, String state, double percentile) {
        return new MarketRiskState.Asset(ticker, name, "SECTOR", "READY", state, 15.0, percentile, 14.0, 2.0, 1.0,
                "1998-12-22", "2026-10-28");
    }

    private static MarketRiskState state(Map<String, Integer> counts, List<MarketRiskState.Asset> assets) {
        return new MarketRiskState("2026-10-28", "2026-10-29T08:25:00Z", "Yahoo Finance via yfinance", "YFINANCE_AUTO_ADJUST",
                new MarketRiskState.Thresholds(70, 90, false), Map.of(), counts, assets);
    }

    @Test
    void summarizesSpyAndElevatedAssetsByPercentile() {
        when(riskState.getLatest()).thenReturn(state(Map.of("NORMAL", 2, "CAUTION", 2), List.of(
                asset("SPY", "S&P 500", "NORMAL", 27.8),
                asset("XLV", "헬스케어", "CAUTION", 74.3),
                asset("XLK", "기술", "NORMAL", 69.6),
                asset("XLP", "필수소비재", "CAUTION", 85.6))));

        new MarketRiskWeeklyNotifier(riskState, notifications).send();

        verify(notifications).createImportantInfoForEnabled("market-risk-weekly:2026-10-28",
                "주간 시장 위험 점검 · S&P 500 정상",
                "기준 거래일 2026-10-28 · 4개 ETF 중 정상 2 · 주의 2 · 위험 0. "
                        + "주의 이상(과거 대비 백분위): 필수소비재(XLP) 85, 헬스케어(XLV) 74. "
                        + "흔들림의 크기만 알려주며 오를지 내릴지는 예측하지 않습니다.",
                "/market");
    }

    @Test
    void allNormalStillSendsTheCheckIn() {
        when(riskState.getLatest()).thenReturn(state(Map.of("NORMAL", 1), List.of(asset("SPY", "S&P 500", "NORMAL", 40.0))));

        new MarketRiskWeeklyNotifier(riskState, notifications).send();

        verify(notifications).createImportantInfoForEnabled("market-risk-weekly:2026-10-28",
                "주간 시장 위험 점검 · S&P 500 정상",
                "기준 거래일 2026-10-28 · 1개 ETF 중 정상 1 · 주의 0 · 위험 0. 주의 이상인 자산이 없습니다. "
                        + "흔들림의 크기만 알려주며 오를지 내릴지는 예측하지 않습니다.",
                "/market");
    }

    @Test
    void missingSnapshotSkipsWithoutNotifying() {
        when(riskState.getLatest()).thenThrow(new ResourceNotFoundException("missing"));

        new MarketRiskWeeklyNotifier(riskState, notifications).send();

        verify(notifications, never()).createImportantInfoForEnabled(anyString(), anyString(), anyString(), anyString());
    }
}
