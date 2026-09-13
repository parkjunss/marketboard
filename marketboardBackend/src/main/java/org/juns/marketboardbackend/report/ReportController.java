package org.juns.marketboardbackend.report;

import java.util.List;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    public record CreateRequest(String ticker) {}
    private final ReportService reports;
    private final IdempotencyService idempotency;
    public ReportController(ReportService reports, IdempotencyService idempotency) { this.reports = reports; this.idempotency = idempotency; }
    @PostMapping
    public ReportService.Detail create(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody CreateRequest request,
            @RequestHeader("Idempotency-Key") String key) {
        String ticker = ReportService.normalize(request.ticker());
        var payload = reports.capture(ticker);
        return idempotency.execute(user.id(), "stock-report-create", key, new CreateRequest(ticker), ReportService.Detail.class,
                () -> reports.save(user.id(), payload));
    }
    @GetMapping
    public List<ReportService.Summary> list(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam String ticker) {
        return reports.list(user.id(), ticker);
    }
    @GetMapping("/{id}")
    public ReportService.Detail get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) { return reports.get(user.id(), id); }
}
