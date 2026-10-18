package org.juns.marketboardbackend.backtest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.juns.marketboardbackend.backtest.dto.PortfolioDesignRequest;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.common.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio/designs")
public class SavedPortfolioDesignController {
    public record SaveRequest(@NotBlank @Size(max=100) String name,
                              @NotBlank @Size(max=40) String selectedId,
                              @NotNull @Valid PortfolioDesignRequest configuration) {}
    private final SavedPortfolioDesignService service;
    private final IdempotencyService idempotency;
    public SavedPortfolioDesignController(SavedPortfolioDesignService service, IdempotencyService idempotency) {
        this.service=service; this.idempotency=idempotency;
    }
    @ExceptionHandler(SavedPortfolioDesignService.CalculationUnavailable.class)
    public ResponseEntity<ErrorResponse> unavailable(SavedPortfolioDesignService.CalculationUnavailable ex) {
        return ResponseEntity.status(503).body(new ErrorResponse(ex.getMessage()));
    }
    @GetMapping
    public List<SavedPortfolioDesignService.Summary> list(@AuthenticationPrincipal AuthenticatedUser user) { return service.list(user.id()); }
    @GetMapping("/{id}")
    public SavedPortfolioDesignService.Detail get(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) { return service.get(user.id(),id); }
    @PostMapping
    public SavedPortfolioDesignService.Detail save(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SaveRequest request, @RequestHeader("Idempotency-Key") String key) {
        var payload=service.capture(request.configuration(),request.selectedId());
        return idempotency.execute(user.id(),"portfolio-design-save",key,request,SavedPortfolioDesignService.Detail.class,
                () -> service.save(user.id(),request.name(),request.selectedId(),payload));
    }
}
