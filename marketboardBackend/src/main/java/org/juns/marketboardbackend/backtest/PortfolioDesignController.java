package org.juns.marketboardbackend.backtest;

import jakarta.validation.Valid;
import org.juns.marketboardbackend.backtest.dto.PortfolioDesignRequest;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PortfolioDesignController {
    private final CollectorClient collector;
    public PortfolioDesignController(CollectorClient collector) { this.collector = collector; }

    @PostMapping("/api/portfolio/design")
    public ResponseEntity<?> design(@Valid @RequestBody PortfolioDesignRequest request) {
        request.validateConditions();
        return collector.designPortfolio(request).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503).body(new ErrorResponse("포트폴리오 계산 서버에 연결할 수 없습니다. 다시 시도해 주세요.")));
    }
}
