package org.juns.marketboardbackend.marketreport;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class MarketReportController {
    private final MarketReportService service;
    public MarketReportController(MarketReportService service) { this.service = service; }
    @GetMapping("/api/market-reports")
    public List<MarketReportService.Summary> list(@RequestParam(required = false) LocalDate before) { return service.list(before); }
    @GetMapping("/api/market-reports/latest")
    public MarketReportService.Latest latest() { return service.latest(); }
    @GetMapping("/api/market-reports/{id}")
    public MarketReportService.Detail get(@PathVariable long id) { return service.get(id); }
    @PostMapping("/api/admin/market-reports/generate")
    public MarketReportService.Generation generate() { return service.generate(); }
}
