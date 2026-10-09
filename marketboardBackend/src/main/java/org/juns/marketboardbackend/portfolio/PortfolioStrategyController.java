package org.juns.marketboardbackend.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}")
public class PortfolioStrategyController {

    public record RuleRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
            @Digits(integer = 1, fraction = 6) BigDecimal maxPositionWeight) {}
    public record ThesisRequest(
            @NotBlank String ticker,
            @NotBlank @Size(max = 2000) String thesis,
            @NotBlank @Size(max = 2000) String invalidationCondition,
            @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 6) BigDecimal targetWeight,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
            @Digits(integer = 1, fraction = 6) BigDecimal maxWeight) {}
    public record ThesisRevisionRequest(
            @NotBlank @Size(max = 2000) String thesis,
            @NotBlank @Size(max = 2000) String invalidationCondition,
            @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 6) BigDecimal targetWeight,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1")
            @Digits(integer = 1, fraction = 6) BigDecimal maxWeight) {}

    private final PortfolioStrategyService service;

    public PortfolioStrategyController(PortfolioStrategyService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public PortfolioStrategyService.RuleResponse getRule(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long portfolioId) {
        return service.getRule(user.id(), portfolioId);
    }

    @PutMapping("/rules")
    public PortfolioStrategyService.RuleResponse putRule(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long portfolioId,
            @Valid @RequestBody RuleRequest request) {
        return service.putRule(user.id(), portfolioId, request.maxPositionWeight());
    }

    @GetMapping("/theses")
    public List<PortfolioStrategyService.ThesisResponse> getTheses(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long portfolioId) {
        return service.getTheses(user.id(), portfolioId);
    }

    @PostMapping("/theses")
    public ResponseEntity<PortfolioStrategyService.ThesisResponse> createThesis(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long portfolioId,
            @Valid @RequestBody ThesisRequest request) {
        var response = service.createThesis(user.id(), portfolioId, request.ticker(), request.thesis(),
                request.invalidationCondition(), request.targetWeight(), request.maxWeight());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/theses/{thesisId}")
    public PortfolioStrategyService.ThesisResponse reviseThesis(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long portfolioId,
            @PathVariable Long thesisId, @Valid @RequestBody ThesisRevisionRequest request) {
        return service.reviseThesis(user.id(), portfolioId, thesisId, request.thesis(),
                request.invalidationCondition(), request.targetWeight(), request.maxWeight());
    }
}
