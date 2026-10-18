package org.juns.marketboardbackend.backtest;

import java.time.Instant;
import java.util.List;
import org.juns.marketboardbackend.backtest.dto.PortfolioDesignRequest;
import org.juns.marketboardbackend.collector.CollectorClient;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SavedPortfolioDesignService {
    public static class CalculationUnavailable extends RuntimeException {
        public CalculationUnavailable() { super("계산 서버에 연결하지 못해 저장하지 않았습니다. 다시 시도해 주세요."); }
    }
    public record Payload(PortfolioDesignRequest configuration, JsonNode result) {}
    public record Summary(Long id, String name, String selectedId, Instant createdAt) {}
    public record Detail(Long id, String name, String selectedId, Instant createdAt, Payload payload) {}
    private final SavedPortfolioDesignRepository repository;
    private final CollectorClient collector;
    private final ObjectMapper mapper;
    public SavedPortfolioDesignService(SavedPortfolioDesignRepository repository, CollectorClient collector, ObjectMapper mapper) {
        this.repository=repository; this.collector=collector; this.mapper=mapper;
    }
    public Payload capture(PortfolioDesignRequest configuration, String selectedId) {
        configuration.validateConditions();
        JsonNode result=collector.designPortfolio(configuration).orElseThrow(CalculationUnavailable::new);
        if (!"READY".equals(result.path("status").asText()))
            throw new IllegalArgumentException("배분안을 계산하지 못해 저장하지 않았습니다. 조건과 데이터 상태를 확인하세요.");
        boolean found=false;
        for (String key : List.of("candidates", "frontier"))
            for (JsonNode candidate : result.path(key))
                if (selectedId.equals(candidate.path("id").asText())) found=true;
        if (!found) throw new IllegalArgumentException("선택한 배분안이 재계산 결과에 없습니다. 다시 비교해 주세요.");
        return new Payload(configuration, result);
    }
    @Transactional
    public Detail save(Long userId, String name, String selectedId, Payload payload) {
        return detail(repository.saveAndFlush(new SavedPortfolioDesign(userId, name.strip(), selectedId, mapper.writeValueAsString(payload))));
    }
    @Transactional(readOnly=true)
    public List<Summary> list(Long userId) {
        return repository.findTop50ByUserIdOrderByIdDesc(userId).stream()
                .map(r -> new Summary(r.getId(), r.getName(), r.getSelectedId(), r.getCreatedAt())).toList();
    }
    @Transactional(readOnly=true)
    public Detail get(Long userId, Long id) {
        return detail(repository.findByIdAndUserId(id,userId)
                .orElseThrow(() -> new ResourceNotFoundException("저장된 설계를 찾을 수 없습니다.")));
    }
    private Detail detail(SavedPortfolioDesign r) {
        return new Detail(r.getId(),r.getName(),r.getSelectedId(),r.getCreatedAt(),mapper.readValue(r.getPayloadJson(),Payload.class));
    }
}
