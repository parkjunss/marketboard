package org.juns.marketboardbackend.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/execution")
public class ExecutionController {
    public record Configuration(
            @NotNull @Pattern(regexp="execution-v1") String version,
            @NotNull @Pattern(regexp="[0-9]{1,10}(\\.[0-9]{1,8})?") String initialCapital,
            @NotNull @Pattern(regexp="[0-9]{1,3}(\\.[0-9]{1,8})?") String feeBps,
            @NotNull @Pattern(regexp="[0-9]{1,10}(\\.[0-9]{1,8})?") String maxOrderValue,
            @NotNull @Pattern(regexp="[01](\\.[0-9]{1,8})?") String maxPositionWeight) {}
    public record Command(
            @NotNull @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String key,
            @NotNull @Pattern(regexp="BUY|SELL|STRATEGY_BUY|STALE_BUY|DISPATCH|TIMEOUT|PARTIAL_FILL|FILL|CANCEL|REJECT|RECONCILE|REVERSE|DUPLICATE|STOP") String operation,
            @Size(max=64) String orderId,
            @Pattern(regexp="[0-9]{1,10}(\\.[0-9]{1,4})?") String quantity) {}
    private final ExecutionService service;
    private final ExecutionOperations operations;
    public ExecutionController(ExecutionService service, ExecutionOperations operations) { this.service = service; this.operations = operations; }
    @PostMapping
    public ExecutionService.Run create(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[a-zA-Z0-9_-]{1,64}") String key,
            @Valid @RequestBody Configuration configuration) { return service.create(user.id(), key, configuration); }
    @GetMapping
    public List<ExecutionService.Run> list(@AuthenticationPrincipal AuthenticatedUser user) { return service.list(user.id()); }
    @GetMapping("/demo")
    public tools.jackson.databind.JsonNode demo() { return service.demo(); }
    @GetMapping("/{id}")
    public ExecutionService.Run get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) { return service.get(user.id(), id); }
    @PostMapping("/{id}/commands")
    public ExecutionService.Run command(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id,
            @Valid @RequestBody Command command) {
        try { return service.apply(user.id(), id, command); }
        catch (IllegalStateException ex) { operations.failure(user.id(), id); throw ex; }
    }
    @PostMapping("/{id}/verify")
    public ExecutionService.Verification verify(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        try { return service.verify(user.id(), id); }
        catch (IllegalStateException ex) { operations.failure(user.id(), id); throw ex; }
    }
    @GetMapping("/{id}/incidents")
    public List<ExecutionOperations.Incident> incidents(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) { return operations.list(user.id(), id); }
    @GetMapping("/{id}/timeline")
    public List<ExecutionService.CommandEntry> timeline(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) { return service.timeline(user.id(), id); }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
    public java.util.Map<String, String> unavailable(IllegalStateException ex) {
        return java.util.Map.of("message", "모의 실행 계산 서버에 연결할 수 없습니다. 같은 요청으로 재시도하세요.");
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> invalidKey() {
        return java.util.Map.of("message", "요청 키 형식이 올바르지 않습니다.");
    }
}
