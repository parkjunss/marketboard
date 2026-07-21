package org.juns.marketboardbackend.marketbreadth;

import org.juns.marketboardbackend.marketbreadth.dto.MarketBreadthResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual trigger for MarketBreadthService's daily cron -- for testing, or forcing a refresh without waiting for the schedule. */
@RestController
@RequestMapping("/api/admin/market-breadth")
public class MarketBreadthAdminController {

    private final MarketBreadthService marketBreadthService;

    public MarketBreadthAdminController(MarketBreadthService marketBreadthService) {
        this.marketBreadthService = marketBreadthService;
    }

    @PostMapping("/recompute")
    public MarketBreadthResponse recompute() {
        marketBreadthService.recompute();
        return marketBreadthService.getLatest();
    }
}
