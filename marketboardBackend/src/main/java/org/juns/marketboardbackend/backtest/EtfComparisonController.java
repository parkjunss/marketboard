package org.juns.marketboardbackend.backtest;

import jakarta.validation.Valid;
import org.juns.marketboardbackend.backtest.dto.EtfComparisonRequest;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EtfComparisonController {
    private final CollectorClient collector;

    public EtfComparisonController(CollectorClient collector) { this.collector = collector; }

    @PostMapping("/api/backtest/etf-comparison")
    public ResponseEntity<?> compare(@Valid @RequestBody EtfComparisonRequest request) {
        request.validatePeriod();
        return collector.compareEtfStrategies(request).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErrorResponse("ETF 전략 비교 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.")));
    }
}
