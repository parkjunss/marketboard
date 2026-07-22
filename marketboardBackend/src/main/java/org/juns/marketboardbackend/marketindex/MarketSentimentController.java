package org.juns.marketboardbackend.marketindex;

import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.FearGreedResponse;
import org.juns.marketboardbackend.collector.PutCallRatioResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.sentiment.PutCallRatioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CNN Fear & Greed Index + options put/call volume ratio. Fear & Greed still proxies the
 * collector directly (low request volume, and CNN's own data already only updates once or so a
 * day); put/call ratio is DB-backed instead -- see PutCallRatioService for why, and for the
 * difference between the bare SPY endpoint (scheduled) and the per-ticker one (lazy, for
 * individual stock detail pages). Both sources are best-effort (CNN's undocumented internal
 * endpoint, yfinance options data), so a failure here is an expected "temporarily unavailable"
 * case, not a bug.
 */
@RestController
@RequestMapping("/api/market-sentiment")
public class MarketSentimentController {

    private final CollectorClient collectorClient;
    private final PutCallRatioService putCallRatioService;

    public MarketSentimentController(CollectorClient collectorClient, PutCallRatioService putCallRatioService) {
        this.collectorClient = collectorClient;
        this.putCallRatioService = putCallRatioService;
    }

    @GetMapping("/fear-greed")
    public FearGreedResponse getFearGreed() {
        return collectorClient.getFearGreed().orElseThrow(() -> new ResourceNotFoundException("Fear & Greed index temporarily unavailable"));
    }

    @GetMapping("/put-call-ratio")
    public PutCallRatioResponse getPutCallRatio() {
        return putCallRatioService.getLatest();
    }

    @GetMapping("/put-call-ratio/{ticker}")
    public PutCallRatioResponse getPutCallRatioForTicker(@PathVariable String ticker) {
        return putCallRatioService.getForTicker(ticker);
    }
}
