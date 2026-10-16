package org.juns.marketboardbackend.screener;

import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EtfMomentumController {
    private final CollectorClient collectorClient;

    public EtfMomentumController(CollectorClient collectorClient) {
        this.collectorClient = collectorClient;
    }

    @GetMapping("/api/screener/etf-momentum")
    public ResponseEntity<?> get() {
        return collectorClient.getEtfMomentum()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new ErrorResponse("ETF 전략 계산 서버에 연결할 수 없습니다. 잠시 후 다시 조회해 주세요.")));
    }
}
