package org.juns.marketboardbackend.review;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.juns.marketboardbackend.collector.MarketIndexCandle;
import org.juns.marketboardbackend.marketindex.MarketIndexHistoryService;
import org.juns.marketboardbackend.marketbreadth.MarketBreadthService;
import org.juns.marketboardbackend.marketbreadth.dto.MarketBreadthResponse;
import org.juns.marketboardbackend.portfolio.PortfolioService;
import org.juns.marketboardbackend.portfolio.dto.*;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReviewService {
    public record Resource<T>(T data, String error) {}
    public record Payload(int schemaVersion, String calculationVersion, int period, Instant startedAt, Instant capturedAt,
            Map<String, Resource<List<MarketIndexCandle>>> histories, Resource<MarketBreadthResponse> breadth,
            Resource<List<PortfolioSummaryResponse>> portfolios, Map<Long, List<PortfolioPositionResponse>> positions) {}
    public record Summary(Long id, int period, Instant createdAt) {}
    public record Detail(Long id, int period, Instant createdAt, Payload payload) {}
    private final InvestmentReviewRepository repository;
    private final MarketIndexHistoryService indices;
    private final MarketBreadthService breadth;
    private final PortfolioService portfolios;
    private final ObjectMapper mapper;
    public ReviewService(InvestmentReviewRepository repository, MarketIndexHistoryService indices,
            MarketBreadthService breadth, PortfolioService portfolios, ObjectMapper mapper) {
        this.repository = repository; this.indices = indices; this.breadth = breadth; this.portfolios = portfolios; this.mapper = mapper;
    }

    // Suspend the write transaction so a missing source can be recorded without marking it rollback-only.
    // Each source retains its own observation time; this is not a synchronized market snapshot.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Payload capture(Long userId, int period) {
        if (period != 5 && period != 21) throw new IllegalArgumentException("점검 주기는 5 또는 21이어야 합니다.");
        Instant started = Instant.now();
        Map<String, Resource<List<MarketIndexCandle>>> histories = new LinkedHashMap<>();
        for (String slug : List.of("SPX", "IXIC", "RUT", "VIX", "US10Y", "USDKRW")) histories.put(slug, read(() -> indices.getHistory(slug)));
        var marketBreadth = read(breadth::getLatest);
        var evidence = read(() -> portfolios.getReviewEvidence(userId));
        Map<Long, List<PortfolioPositionResponse>> positions = new LinkedHashMap<>();
        Resource<List<PortfolioSummaryResponse>> summaries;
        if (evidence.data() == null) summaries = new Resource<>(null, evidence.error());
        else {
            for (var item : evidence.data()) positions.put(item.summary().id(), item.positions());
            summaries = new Resource<>(evidence.data().stream().map(PortfolioService.ReviewEvidence::summary).toList(), null);
        }
        return new Payload(1, "observed-bars-v1", period, started, Instant.now(), histories, marketBreadth, summaries, positions);
    }
    private <T> Resource<T> read(Supplier<T> supplier) {
        try { return new Resource<>(supplier.get(), null); }
        catch (RuntimeException ex) { return new Resource<>(null, "자료 조회 실패 · 저장 당시 이용 불가"); }
    }
    @Transactional
    public Detail save(Long userId, Payload payload) {
        var row = repository.saveAndFlush(new InvestmentReview(userId, payload.period(), mapper.writeValueAsString(payload)));
        return detail(row);
    }
    @Transactional(readOnly = true)
    public List<Summary> list(Long userId) {
        return repository.findTop50ByUserIdOrderByIdDesc(userId).stream().map(row -> new Summary(row.getId(), row.getPeriod(), row.getCreatedAt())).toList();
    }
    @Transactional(readOnly = true)
    public Detail get(Long userId, Long id) {
        return detail(repository.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("점검 기록을 찾을 수 없습니다.")));
    }
    private Detail detail(InvestmentReview row) {
        return new Detail(row.getId(), row.getPeriod(), row.getCreatedAt(), mapper.readValue(row.getPayloadJson(), Payload.class));
    }
}
