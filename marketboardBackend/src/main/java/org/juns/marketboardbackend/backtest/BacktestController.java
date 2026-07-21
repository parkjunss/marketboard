package org.juns.marketboardbackend.backtest;

import jakarta.validation.Valid;
import java.util.List;
import org.juns.marketboardbackend.backtest.dto.BacktestRunRequest;
import org.juns.marketboardbackend.backtest.dto.BacktestRunResponse;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backtest/runs")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping
    public BacktestRunResponse run(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody BacktestRunRequest request) {
        return backtestService.run(principal.id(), request);
    }

    @GetMapping
    public List<BacktestRunResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return backtestService.list(principal.id());
    }

    @GetMapping("/{id}")
    public BacktestRunResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return backtestService.get(principal.id(), id);
    }
}
