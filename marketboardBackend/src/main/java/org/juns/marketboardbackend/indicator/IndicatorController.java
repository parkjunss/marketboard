package org.juns.marketboardbackend.indicator;

import java.util.List;
import org.juns.marketboardbackend.indicator.dto.IndicatorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/indicators")
public class IndicatorController {

    private final IndicatorRepository indicatorRepository;

    public IndicatorController(IndicatorRepository indicatorRepository) {
        this.indicatorRepository = indicatorRepository;
    }

    @GetMapping("/{ticker}")
    public List<IndicatorResponse> getIndicators(
            @PathVariable String ticker, @RequestParam(defaultValue = "1d") String timeframe) {
        return indicatorRepository.findBySymbol_TickerIgnoreCaseAndTimeframe(ticker, timeframe).stream()
                .map(IndicatorResponse::from)
                .toList();
    }
}
