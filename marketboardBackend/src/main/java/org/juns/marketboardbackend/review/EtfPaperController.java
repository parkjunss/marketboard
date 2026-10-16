package org.juns.marketboardbackend.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/etf/paper")
public class EtfPaperController {
    public record Start(@NotNull Long reviewId) {}
    private final EtfPaperService service;
    private final IdempotencyService idempotency;
    public EtfPaperController(EtfPaperService service, IdempotencyService idempotency) {
        this.service = service; this.idempotency = idempotency;
    }
    @PostMapping
    public EtfPaperService.Account start(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody Start request, @RequestHeader("Idempotency-Key") String key) {
        return idempotency.execute(user.id(), "etf-paper-start", key, request, EtfPaperService.Account.class,
                () -> service.start(user.id(), request.reviewId()));
    }
    @GetMapping
    public List<EtfPaperService.Account> list(@AuthenticationPrincipal AuthenticatedUser user) { return service.list(user.id()); }
    @GetMapping("/{id}/snapshots")
    public List<EtfPaperService.Snapshot> snapshots(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.snapshots(user.id(), id);
    }
    @PostMapping("/{id}/refresh")
    public EtfPaperService.Account refresh(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.refresh(user.id(), id);
    }
    @PostMapping("/{id}/stop")
    public EtfPaperService.Account stop(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return service.stop(user.id(), id);
    }
}
