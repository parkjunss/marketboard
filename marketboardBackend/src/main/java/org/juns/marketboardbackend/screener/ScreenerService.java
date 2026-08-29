package org.juns.marketboardbackend.screener;

import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.MomentumScreenerRequest;
import org.juns.marketboardbackend.collector.MomentumScreenerResult;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Momentum stock screener over the S&P 500 universe -- always a live run against the collector, no
 * DB persistence (unlike BacktestService): a screener result is "today's picks", not a
 * config/history a user would revisit, so there's nothing worth keeping around between requests.
 */
@Service
public class ScreenerService {

    private final CollectorClient collectorClient;

    public ScreenerService(CollectorClient collectorClient) {
        this.collectorClient = collectorClient;
    }

    public MomentumScreenerResult runMomentumScreener(MomentumScreenerRequest request) {
        return collectorClient
                .getMomentumScreener(request)
                .orElseThrow(() -> new ResourceNotFoundException("모멘텀 스크리너 결과를 가져오지 못했습니다"));
    }
}
