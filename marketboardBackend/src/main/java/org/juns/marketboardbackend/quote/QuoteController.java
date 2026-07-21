package org.juns.marketboardbackend.quote;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.juns.marketboardbackend.quote.dto.CandleResponse;
import org.juns.marketboardbackend.quote.dto.QuoteResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping
    public List<QuoteResponse> getQuotes() {
        return quoteService.getActiveQuotes();
    }

    @GetMapping("/{ticker}")
    public QuoteResponse getQuote(@PathVariable String ticker) {
        return quoteService.getQuote(ticker);
    }

    @GetMapping("/{ticker}/history")
    public List<CandleResponse> getHistory(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1m") String timeframe,
            @RequestParam(defaultValue = "200") @Min(1) @Max(1500) int limit) {
        return quoteService.getHistory(ticker, timeframe, limit);
    }
}
