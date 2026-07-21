package org.juns.marketboardbackend.symbol;

import org.juns.marketboardbackend.collector.SymbolProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Company profile (sector/industry/market cap/...) for the symbol detail page. See {@link SymbolProfileService}. */
@RestController
@RequestMapping("/api/symbols")
public class SymbolProfileController {

    private final SymbolProfileService symbolProfileService;

    public SymbolProfileController(SymbolProfileService symbolProfileService) {
        this.symbolProfileService = symbolProfileService;
    }

    @GetMapping("/{ticker}/profile")
    public SymbolProfileResponse getProfile(@PathVariable String ticker) {
        return symbolProfileService.getProfile(ticker);
    }
}
