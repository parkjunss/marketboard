package org.juns.marketboardbackend.backtest;

import java.util.List;
import java.util.Optional;
import org.juns.marketboardbackend.backtest.dto.BacktestRunRequest;
import org.juns.marketboardbackend.backtest.dto.BacktestRunResponse;
import org.juns.marketboardbackend.collector.BacktestEngineRequest;
import org.juns.marketboardbackend.collector.BacktestEngineResult;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs a backtest synchronously: the collector call blocks the request thread, same as
 * FinancialsService/SymbolProfileService. Fine for Phase 1's scope (a handful of tickers, daily
 * bars, buy & hold math) -- CollectorClient's 10s timeout is comfortably above what that actually
 * takes. Heavier strategies (indicator-condition backtests over many symbols/years) should switch
 * this to an async job instead of raising the timeout, not before.
 */
@Service
public class BacktestService {

    private final BacktestRunRepository backtestRunRepository;
    private final UserRepository userRepository;
    private final CollectorClient collectorClient;
    private final ObjectMapper objectMapper;

    public BacktestService(
            BacktestRunRepository backtestRunRepository,
            UserRepository userRepository,
            CollectorClient collectorClient,
            ObjectMapper objectMapper) {
        this.backtestRunRepository = backtestRunRepository;
        this.userRepository = userRepository;
        this.collectorClient = collectorClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BacktestRunResponse run(Long userId, BacktestRunRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다");
        }

        User user = userRepository.getReferenceById(userId);
        String configJson = objectMapper.writeValueAsString(request);
        BacktestRun run = BacktestRun.builder().user(user).name(request.name()).configJson(configJson).build();
        backtestRunRepository.save(run);

        BacktestEngineRequest engineRequest = new BacktestEngineRequest(
                request.tickers(), request.startDate(), request.endDate(), request.initialCapital(), request.riskFreeRate());
        Optional<BacktestEngineResult> result = collectorClient.runBacktest(engineRequest);

        if (result.isEmpty()) {
            run.fail("백테스트 엔진 호출에 실패했습니다. 잠시 후 다시 시도해주세요.");
            return toResponse(run, request, null);
        }

        run.complete(objectMapper.writeValueAsString(result.get()));
        return toResponse(run, request, result.get());
    }

    @Transactional(readOnly = true)
    public List<BacktestRunResponse> list(Long userId) {
        return backtestRunRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BacktestRunResponse get(Long userId, Long id) {
        BacktestRun run = backtestRunRepository
                .findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Backtest run not found: " + id));
        return toResponse(run);
    }

    private BacktestRunResponse toResponse(BacktestRun run) {
        BacktestRunRequest config = objectMapper.readValue(run.getConfigJson(), BacktestRunRequest.class);
        BacktestEngineResult result =
                run.getResultJson() != null ? objectMapper.readValue(run.getResultJson(), BacktestEngineResult.class) : null;
        return toResponse(run, config, result);
    }

    private BacktestRunResponse toResponse(BacktestRun run, BacktestRunRequest config, BacktestEngineResult result) {
        return new BacktestRunResponse(
                run.getId(),
                run.getName(),
                run.getStatus().name(),
                config.tickers(),
                config.startDate(),
                config.endDate(),
                config.initialCapital(),
                config.riskFreeRate(),
                result,
                run.getErrorMessage(),
                run.getCreatedAt(),
                run.getCompletedAt());
    }
}
