package org.juns.marketboardbackend.portfolio;

import jakarta.validation.Valid;
import java.util.List;
import org.juns.marketboardbackend.portfolio.dto.PortfolioCreateRequest;
import org.juns.marketboardbackend.portfolio.dto.PortfolioPositionRequest;
import org.juns.marketboardbackend.portfolio.dto.PortfolioPositionResponse;
import org.juns.marketboardbackend.portfolio.dto.PortfolioPositionUpdateRequest;
import org.juns.marketboardbackend.portfolio.dto.PortfolioSummaryResponse;
import org.juns.marketboardbackend.portfolio.dto.PortfolioUpdateRequest;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public List<PortfolioSummaryResponse> getPortfolios(@AuthenticationPrincipal AuthenticatedUser principal) {
        return portfolioService.getPortfolios(principal.id());
    }

    @PostMapping
    public ResponseEntity<PortfolioSummaryResponse> createPortfolio(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody PortfolioCreateRequest request) {
        PortfolioSummaryResponse response = portfolioService.createPortfolio(principal.id(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public PortfolioSummaryResponse renamePortfolio(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody PortfolioUpdateRequest request) {
        return portfolioService.renamePortfolio(principal.id(), id, request.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePortfolio(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        portfolioService.deletePortfolio(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/positions")
    public List<PortfolioPositionResponse> getPositions(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return portfolioService.getPositions(principal.id(), id);
    }

    @PostMapping("/{id}/positions")
    public ResponseEntity<PortfolioPositionResponse> addPosition(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody PortfolioPositionRequest request) {
        PortfolioPositionResponse response = portfolioService.addPosition(principal.id(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/positions/{positionId}")
    public PortfolioPositionResponse updatePosition(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @PathVariable Long positionId,
            @Valid @RequestBody PortfolioPositionUpdateRequest request) {
        return portfolioService.updatePosition(principal.id(), id, positionId, request);
    }

    @DeleteMapping("/{id}/positions/{positionId}")
    public ResponseEntity<Void> removePosition(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id, @PathVariable Long positionId) {
        portfolioService.removePosition(principal.id(), id, positionId);
        return ResponseEntity.noContent().build();
    }
}
