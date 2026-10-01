package org.juns.marketboardbackend.screener;

import java.math.BigDecimal;
import jakarta.validation.Valid;
import org.juns.marketboardbackend.collector.MomentumScreenerRequest;
import org.juns.marketboardbackend.collector.MomentumScreenerResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.juns.marketboardbackend.screener.dto.ScreenerSearchRequest;
import org.juns.marketboardbackend.screener.dto.ScreenerSearchResponse;

@RestController
@RequestMapping("/api/screener")
public class ScreenerController {

    private final ScreenerService screenerService;

    public ScreenerController(ScreenerService screenerService) {
        this.screenerService = screenerService;
    }

    @GetMapping("/momentum")
    public MomentumScreenerResult momentum(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) Integer momentumWindowDays,
            @RequestParam(required = false) Integer trendMaWindow,
            @RequestParam(required = false) BigDecimal correlationThreshold,
            @RequestParam(required = false) BigDecimal minMomentumPct,
            @RequestParam(required = false) BigDecimal maxRsi,
            @RequestParam(required = false) BigDecimal minMarketCap,
            @RequestParam(required = false) BigDecimal minRevenue) {
        return screenerService.runMomentumScreener(new MomentumScreenerRequest(
                topN, momentumWindowDays, trendMaWindow, correlationThreshold, minMomentumPct, maxRsi, minMarketCap, minRevenue));
    }

    @PostMapping("/search")
    public ScreenerSearchResponse search(@Valid @RequestBody ScreenerSearchRequest request) {
        return screenerService.search(request);
    }
}
