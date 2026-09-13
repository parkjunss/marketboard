package org.juns.marketboardbackend.report;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.juns.marketboardbackend.analysis.AnalysisService;
import org.juns.marketboardbackend.collector.*;
import org.juns.marketboardbackend.financials.*;
import org.juns.marketboardbackend.quote.*;
import org.juns.marketboardbackend.quote.dto.CandleResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReportService {
    public record Resource<T>(T data, String error) {}
    public record FinancialEvidence(FinancialsResponse statements, Instant fetchedAt) {}
    public record Payload(int schemaVersion, String calculationVersion, String ticker, Instant startedAt, Instant capturedAt,
            Resource<ResolvedPrice> price, Resource<List<CandleResponse>> history,
            Resource<FinancialEvidence> financials, Resource<StockAnalysisResult> analysis, List<String> checks) {}
    public record Summary(Long id, String ticker, Instant createdAt) {}
    public record Detail(Long id, String ticker, Instant createdAt, Payload payload) {}
    private final StockReportRepository repository;
    private final QuoteService quotes;
    private final FinancialsService financials;
    private final FinancialStatementRepository statements;
    private final AnalysisService analysis;
    private final ObjectMapper mapper;
    public ReportService(StockReportRepository repository, QuoteService quotes, FinancialsService financials,
            FinancialStatementRepository statements, AnalysisService analysis, ObjectMapper mapper) {
        this.repository = repository; this.quotes = quotes; this.financials = financials;
        this.statements = statements; this.analysis = analysis; this.mapper = mapper;
    }
    public static String normalize(String ticker) {
        if (ticker == null || !ticker.trim().matches("[A-Za-z0-9^][A-Za-z0-9.^=-]{0,19}"))
            throw new IllegalArgumentException("올바른 종목 코드를 입력하세요.");
        return ticker.trim().toUpperCase(Locale.ROOT);
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Payload capture(String input) {
        String ticker = normalize(input);
        Instant started = Instant.now();
        var price = read(() -> quotes.resolvePrice(ticker).orElse(null));
        var history = read(() -> quotes.getHistory(ticker, "1d", 252));
        var financial = read(() -> {
            // Read the persisted payload and its timestamp together; never label stale fallback as newly fetched.
            financials.getFinancials(ticker);
            return statements.findByTickerIgnoreCase(ticker)
                    .map(row -> new FinancialEvidence(mapper.readValue(row.getPayloadJson(), FinancialsResponse.class), row.getFetchedAt()))
                    .orElse(null);
        });
        var quant = read(() -> analysis.analyze(ticker, 252, 21, 500));
        List<String> checks = new ArrayList<>();
        if (price.data() == null) checks.add("기준 가격을 확보하지 못했습니다.");
        else if (!"RECENT".equals(price.data().status())) checks.add("가격이 오래되었거나 최신성을 확인할 수 없습니다.");
        if (history.data() == null || history.data().isEmpty()) checks.add("일별 가격 이력이 없습니다.");
        if (financial.data() == null) checks.add("재무 자료를 확보하지 못했습니다.");
        else if (financial.data().fetchedAt() == null || financial.data().fetchedAt().isBefore(started.minus(Duration.ofHours(24))))
            checks.add("재무 캐시가 24시간을 초과했거나 수집 시각을 확인할 수 없습니다.");
        if (quant.data() == null) checks.add("정량 위험 분석을 확보하지 못했습니다.");
        else if (quant.data().asOfDate() == null || quant.data().asOfDate().isBefore(LocalDate.ofInstant(started, ZoneOffset.UTC).minusDays(7)))
            checks.add("정량 분석 기준일이 7일을 초과했거나 확인되지 않습니다.");
        checks.add("재무 공시일·원문·통화·주식분할 조정 여부는 현재 공급 데이터에서 검증되지 않았습니다. 수집 시각은 공시일이 아닙니다.");
        checks.add("각 자료의 관측 시점은 다릅니다. 최신 공시 반영 여부와 지표 간 기간 일치는 별도 확인이 필요합니다.");
        return new Payload(1, "stock-report-v1", ticker, started, Instant.now(), price, history, financial, quant, List.copyOf(checks));
    }
    private <T> Resource<T> read(Supplier<T> supplier) {
        try { T data = supplier.get(); return new Resource<>(data, data == null ? "자료 없음" : null); }
        catch (RuntimeException ex) { return new Resource<>(null, "생성 당시 자료 조회 실패"); }
    }
    @Transactional
    public Detail save(Long userId, Payload payload) {
        // Validate only on a new write: a replay must still return the old report during an upstream outage.
        if (payload.price().data() == null && (payload.history().data() == null || payload.history().data().isEmpty())
                && payload.financials().data() == null && payload.analysis().data() == null)
            throw new ResourceNotFoundException("이 종목의 보고서 자료를 조회하지 못했습니다. 잠시 후 다시 시도하세요.");
        return detail(repository.saveAndFlush(new StockReport(userId, payload.ticker(), mapper.writeValueAsString(payload))));
    }
    @Transactional(readOnly = true)
    public List<Summary> list(Long userId, String ticker) {
        return repository.findTop50ByUserIdAndTickerOrderByIdDesc(userId, normalize(ticker)).stream()
                .map(r -> new Summary(r.getId(), r.getTicker(), r.getCreatedAt())).toList();
    }
    @Transactional(readOnly = true)
    public Detail get(Long userId, Long id) {
        return detail(repository.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("보고서를 찾을 수 없습니다.")));
    }
    private Detail detail(StockReport row) {
        return new Detail(row.getId(), row.getTicker(), row.getCreatedAt(), mapper.readValue(row.getPayloadJson(), Payload.class));
    }
}
