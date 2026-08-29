package org.juns.marketboardbackend.analysis;

import org.juns.marketboardbackend.collector.StockAnalysisResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/{ticker}")
    public StockAnalysisResult analyze(
            @PathVariable String ticker,
            @RequestParam(required = false) Integer lookbackDays,
            @RequestParam(required = false) Integer monteCarloHorizonDays,
            @RequestParam(required = false) Integer monteCarloPaths) {
        return analysisService.analyze(ticker, lookbackDays, monteCarloHorizonDays, monteCarloPaths);
    }
}
