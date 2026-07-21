package org.juns.marketboardbackend.marketbreadth;

import org.juns.marketboardbackend.marketbreadth.dto.MarketBreadthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-breadth")
public class MarketBreadthController {

    private final MarketBreadthService marketBreadthService;

    public MarketBreadthController(MarketBreadthService marketBreadthService) {
        this.marketBreadthService = marketBreadthService;
    }

    @GetMapping
    public MarketBreadthResponse getLatest() {
        return marketBreadthService.getLatest();
    }
}
