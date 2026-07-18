package org.juns.marketboardbackend.financials;

import org.juns.marketboardbackend.collector.FinancialsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/financials")
public class FinancialsController {

    private final FinancialsService financialsService;

    public FinancialsController(FinancialsService financialsService) {
        this.financialsService = financialsService;
    }

    @GetMapping("/{ticker}")
    public FinancialsResponse getFinancials(@PathVariable String ticker) {
        return financialsService.getFinancials(ticker);
    }
}
