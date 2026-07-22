package org.juns.marketboardbackend.marketindex;

import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.FearGreedResponse;
import org.juns.marketboardbackend.collector.OptionsLevelsResponse;
import org.juns.marketboardbackend.collector.PutCallRatioResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.sentiment.OptionsLevelsService;
import org.juns.marketboardbackend.sentiment.PutCallRatioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CNN Fear & Greed Index + options put/call volume ratio + options support/resistance levels.
 * Fear & Greed still proxies the collector directly (low request volume, and CNN's own data
 * already only updates once or so a day); put/call ratio and options-levels are DB-backed instead
 * -- see PutCallRatioService/OptionsLevelsService for why, and for the difference between the
 * bare SPY put/call endpoint (scheduled) and the per-ticker ones (lazy, for individual stock
 * detail pages). All sources are best-effort (CNN's undocumented internal endpoint, yfinance
 * options data, CBOE's public delayed quotes), so a failure here is an expected "temporarily
 * unavailable" case, not a bug.
 */
@RestController
@RequestMapping("/api/market-sentiment")
public class MarketSentimentController {

    private final CollectorClient collectorClient;
    private final PutCallRatioService putCallRatioService;
    private final OptionsLevelsService optionsLevelsService;

    public MarketSentimentController(
            CollectorClient collectorClient, PutCallRatioService putCallRatioService, OptionsLevelsService optionsLevelsService) {
        this.collectorClient = collectorClient;
        this.putCallRatioService = putCallRatioService;
        this.optionsLevelsService = optionsLevelsService;
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

    @GetMapping("/options-levels/{ticker}")
    public OptionsLevelsResponse getOptionsLevels(@PathVariable String ticker) {
        return optionsLevelsService.getForTicker(ticker);
    }
}
