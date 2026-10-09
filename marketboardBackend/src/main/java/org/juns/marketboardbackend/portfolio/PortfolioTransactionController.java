package org.juns.marketboardbackend.portfolio;

import jakarta.validation.Valid;
import java.util.List;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.portfolio.dto.PortfolioTransactionRequest;
import org.juns.marketboardbackend.portfolio.dto.PortfolioTransactionResponse;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/transactions")
public class PortfolioTransactionController {

    private final PortfolioTransactionService service;
    private final IdempotencyService idempotency;

    public PortfolioTransactionController(PortfolioTransactionService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping
    public List<PortfolioTransactionResponse> getTransactions(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long portfolioId) {
        return service.getTransactions(principal.id(), portfolioId);
    }

    @PostMapping
    public ResponseEntity<PortfolioTransactionResponse> createTransaction(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long portfolioId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PortfolioTransactionRequest request) {
        PortfolioTransactionResponse response = idempotency.execute(
                principal.id(), "portfolio-transaction:" + portfolioId, key, request,
                PortfolioTransactionResponse.class,
                () -> service.create(principal.id(), portfolioId, request, key));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
