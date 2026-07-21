package org.juns.marketboardbackend.marketindex;

import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.FearGreedResponse;
import org.juns.marketboardbackend.collector.PutCallRatioResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CNN Fear & Greed Index + options put/call volume ratio -- proxies the collector directly with
 * no DB caching, same reasoning as MarketIndexController (low request volume). Both sources are
 * best-effort (CNN's undocumented internal endpoint, yfinance options data), so a failure here is
 * an expected "temporarily unavailable" case, not a bug.
 */
@RestController
@RequestMapping("/api/market-sentiment")
public class MarketSentimentController {

    private final CollectorClient collectorClient;

    public MarketSentimentController(CollectorClient collectorClient) {
        this.collectorClient = collectorClient;
    }

    @GetMapping("/fear-greed")
    public FearGreedResponse getFearGreed() {
        return collectorClient.getFearGreed().orElseThrow(() -> new ResourceNotFoundException("Fear & Greed index temporarily unavailable"));
    }

    @GetMapping("/put-call-ratio")
    public PutCallRatioResponse getPutCallRatio(@RequestParam(defaultValue = "SPY") String ticker) {
        return collectorClient
                .getPutCallRatio(ticker)
                .orElseThrow(() -> new ResourceNotFoundException("Put/call ratio temporarily unavailable for " + ticker));
    }
}
