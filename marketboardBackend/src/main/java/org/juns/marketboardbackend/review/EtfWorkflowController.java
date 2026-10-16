package org.juns.marketboardbackend.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.juns.marketboardbackend.backtest.dto.EtfComparisonRequest;
import org.juns.marketboardbackend.collector.*;
import org.juns.marketboardbackend.common.IdempotencyService;
import org.juns.marketboardbackend.portfolio.*;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/etf")
public class EtfWorkflowController {
    public record Capture(@NotNull @Valid EtfComparisonRequest configuration,
            @NotNull @Pattern(regexp = "SPY_HOLD|MOMENTUM|MOMENTUM_TREND") String strategyId) {}
    public record Proposal(@NotNull Long reviewId, @NotNull Long portfolioId,
            @NotNull @DecimalMin("0") @DecimalMax("1000000000") BigDecimal cashUsd) {}
    public record Summary(Long id, Instant createdAt, String strategyId) {}
    private final ReviewService reviews;
    private final CollectorClient collector;
    private final IdempotencyService idempotency;
    private final PortfolioService portfolios;
    private final PortfolioStrategyService rules;

    public EtfWorkflowController(ReviewService reviews, CollectorClient collector, IdempotencyService idempotency,
            PortfolioService portfolios, PortfolioStrategyService rules) {
        this.reviews = reviews; this.collector = collector; this.idempotency = idempotency;
        this.portfolios = portfolios; this.rules = rules;
    }
    @PostMapping("/reviews")
    public ReviewService.Detail capture(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody Capture request, @RequestHeader("Idempotency-Key") String key) {
        request.configuration().validatePeriod();
        return idempotency.execute(user.id(), "etf-review", key, request, ReviewService.Detail.class, () -> {
            Instant started = Instant.now();
            var evidence = collector.captureEtfResearch(request.configuration(), request.strategyId())
                    .orElseThrow(EtfWorkflowController::unavailable);
            if (!evidence.comparison().ready()) throw new IllegalArgumentException("전략 비교 자료가 부족합니다.");
            return reviews.save(user.id(), new ReviewService.Payload(4, evidence.calculationVersion(), 21,
                    started, Instant.now(), Map.of(), new ReviewService.Resource<>(null, "ETF 전략 전용 기록"),
                    new ReviewService.Resource<>(null, "ETF 전략 전용 기록"), Map.of(),
                    new ReviewService.Resource<>(null, "ETF 전략 전용 기록"),
                    new ReviewService.Resource<>(null, "ETF 전략 전용 기록"), evidence));
        });
    }
    @GetMapping("/reviews")
    public List<Summary> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return reviews.list(user.id()).stream().map(s -> reviews.get(user.id(), s.id()))
                .filter(d -> d.payload().etf() != null)
                .map(d -> new Summary(d.id(), d.createdAt(), d.payload().etf().strategyId())).toList();
    }
    @PostMapping("/rebalance")
    public JsonNode propose(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody Proposal request) {
        var evidence = reviews.get(user.id(), request.reviewId()).payload().etf();
        if (evidence == null || !"etf-momentum-v1".equals(evidence.calculationVersion()))
            throw new IllegalArgumentException("지원하는 ETF 전략 기록이 아닙니다.");
        var positions = portfolios.getPositions(user.id(), request.portfolioId());
        var rule = rules.getRule(user.id(), request.portfolioId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategyId", evidence.strategyId());
        payload.put("holdings", positions.stream().map(p -> Map.of("ticker", p.ticker(),
                "quantity", p.quantity(), "version", p.version())).toList());
        payload.put("cashUsd", request.cashUsd());
        payload.put("feeBps", evidence.configuration().feeBps());
        payload.put("slippageBps", evidence.configuration().slippageBps());
        payload.put("maxPositionWeight", rule == null ? null : rule.maxPositionWeight());
        return collector.proposeEtfRebalance(payload).orElseThrow(EtfWorkflowController::unavailable);
    }
    static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ETF 계산 서버를 이용할 수 없습니다.");
    }
}
