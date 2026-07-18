package org.juns.marketboardbackend.symbol;

import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.collector.SymbolProfileResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company profile (sector/industry/market cap) for the symbol detail page. Proxies the
 * collector's yfinance-backed lookup directly with no DB caching, same reasoning as
 * {@link org.juns.marketboardbackend.marketindex.MarketIndexController}: low request volume.
 */
@RestController
@RequestMapping("/api/symbols")
public class SymbolProfileController {

    private final CollectorClient collectorClient;

    public SymbolProfileController(CollectorClient collectorClient) {
        this.collectorClient = collectorClient;
    }

    @GetMapping("/{ticker}/profile")
    public SymbolProfileResponse getProfile(@PathVariable String ticker) {
        return collectorClient
                .getSymbolProfile(ticker)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown ticker " + ticker));
    }
}
