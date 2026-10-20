package org.juns.marketboardbackend.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/auto-trading")
public class AutoTradingController {
    public record Configuration(
            @NotNull @Pattern(regexp="sma-paper-v1") String version,
            @NotNull @Pattern(regexp="[A-Z0-9.^-]{1,16}") String ticker,
            @NotNull LocalDate startDate, @NotNull LocalDate endDate,
            @Min(2) @Max(200) int window,
            @NotNull @Pattern(regexp="[0-9]{1,9}(\\.[0-9]{1,8})?") String initialCapital,
            @NotNull @Pattern(regexp="[0-9]{1,3}(\\.[0-9]{1,8})?") String feeBps,
            @NotNull @Pattern(regexp="[0-9]{1,3}(\\.[0-9]{1,8})?") String slippageBps,
            @NotNull @Pattern(regexp="[01](\\.[0-9]{1,8})?") String maxPositionWeight,
            @NotNull @Pattern(regexp="[0-9]{1,9}(\\.[0-9]{1,8})?") String maxOrderValue,
            @Min(2) @Max(300) int intervalSeconds) {}
    public record Control(@NotNull @Pattern(regexp="PAUSE|RESUME|STOP") String action) {}
    public record SaveStrategy(@NotBlank @Size(max=80) String name, @Positive long sourceRunId,
                               @NotNull @Valid Configuration configuration) {}
    public record ValidationPeriod(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    public record CostScenario(@NotNull @Pattern(regexp="[0-9]{1,3}(\\.[0-9]{1,8})?") String feeBps,
                               @NotNull @Pattern(regexp="[0-9]{1,3}(\\.[0-9]{1,8})?") String slippageBps) {}
    public record CostRequest(@NotNull @Size(min=1,max=8) List<@NotNull @Valid CostScenario> scenarios) {}
    private final AutoTradingService service;
    public AutoTradingController(AutoTradingService service) { this.service = service; }
    @GetMapping("/prices")
    public List<AutoTradingService.PriceRange> prices() { return service.prices(); }
    @GetMapping
    public List<AutoTradingService.Run> list(@AuthenticationPrincipal AuthenticatedUser user) { return service.list(user.id()); }
    @GetMapping("/strategies")
    public List<AutoTradingService.Strategy> strategies(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.strategies(user.id());
    }
    @GetMapping("/validations")
    public List<AutoTradingService.ValidationSummary> validations(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.validations(user.id());
    }
    @PostMapping("/validations/{id}/cost-analyses")
    public AutoTradingService.CostAnalysis analyzeCosts(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String key,
            @Valid @RequestBody CostRequest request) {
        return service.analyzeCosts(user.id(), key, id, request);
    }
    @GetMapping("/validations/{id}/cost-analyses")
    public List<AutoTradingService.CostSummary> costAnalyses(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return service.costAnalyses(user.id(), id);
    }
    @GetMapping("/cost-analyses/{id}")
    public AutoTradingService.CostAnalysis costAnalysis(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return service.costAnalysis(user.id(), id);
    }
    @GetMapping("/cost-analyses/{id}/export")
    public CostReport.Export exportCosts(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @RequestParam @Pattern(regexp="csv|html") String format) {
        return CostReport.export(service.costAnalysis(user.id(), id), format);
    }
    @GetMapping("/validations/{id}")
    public AutoTradingService.Validation validation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return service.validation(user.id(), id);
    }
    @PostMapping("/validations/{id}/verify")
    public Map<String, Boolean> verifyValidation(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return service.verifyValidation(user.id(), id);
    }
    @PostMapping("/strategies/{id}/validations")
    public AutoTradingService.Validation validatePeriod(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String key,
            @Valid @RequestBody ValidationPeriod period) {
        return service.validatePeriod(user.id(), key, id, period);
    }
    @PostMapping("/strategies")
    public AutoTradingService.Strategy saveStrategy(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String key,
            @Valid @RequestBody SaveStrategy request) {
        return service.saveStrategy(user.id(), key, request);
    }
    @PostMapping("/strategies/{id}/runs")
    public AutoTradingService.Run runStrategy(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String key) {
        return service.runStrategy(user.id(), key, id);
    }
    @GetMapping("/{id}/compare/{otherId}")
    public AutoTradingService.RunComparison compareRuns(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long id, @PathVariable long otherId) {
        return service.compareRuns(user.id(), id, otherId);
    }
    @PostMapping
    public AutoTradingService.Run create(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String key,
            @Valid @RequestBody Configuration configuration) { return service.create(user.id(), key, configuration); }
    @GetMapping("/{id}")
    public AutoTradingService.Detail detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return service.detail(user.id(), id);
    }
    @PostMapping("/{id}/control")
    public AutoTradingService.Run control(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @Valid @RequestBody Control control) { return service.control(user.id(), id, control.action()); }
    @GetMapping("/{id}/comparison")
    public tools.jackson.databind.JsonNode comparison(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return service.comparison(user.id(), id);
    }
    @PostMapping("/{id}/verify")
    public Map<String, Object> verify(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return service.verify(user.id(), id);
    }
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> unavailable() { return Map.of("message", "모의 계산 서버를 사용할 수 없습니다. 기록을 유지했습니다."); }
}
