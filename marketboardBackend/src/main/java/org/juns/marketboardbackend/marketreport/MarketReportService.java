package org.juns.marketboardbackend.marketreport;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.juns.marketboardbackend.collector.*;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.marketbreadth.MarketBreadthService;
import org.juns.marketboardbackend.marketbreadth.dto.MarketBreadthResponse;
import org.juns.marketboardbackend.marketindex.MarketIndexHistoryService;
import org.juns.marketboardbackend.news.NewsService;
import org.juns.marketboardbackend.sectorperformance.SectorPerformanceService;
import org.juns.marketboardbackend.sentiment.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class MarketReportService {
    public record Session(LocalDate tradingDate, Instant closedAt, String calendar, String calendarVersion) {}
    public record Index(String slug, String name, String unit, double close, Double changePct1d,
            Double changePct1w, Double changePct1m, MarketIndexCandle evidence) {}
    public record Headline(String headline, String source, Instant publishedAt, String url) {}
    public record Highlight(String text, String section) {}
    public record Payload(int schemaVersion, String calculationVersion, Session session,
            Instant startedAt, Instant capturedAt, List<Highlight> highlights, List<Index> indices,
            List<SectorPerformance> sectors, MarketBreadthResponse breadth, List<Index> macro,
            FearGreedResponse fearGreed, PutCallRatioTrendResponse putCall, List<Headline> news, List<String> checks) {}
    public record Summary(Long id, String market, LocalDate tradingDate, int version, String status, Instant publishedAt) {}
    public record Detail(Summary report, Payload payload) {}
    public record Latest(LocalDate expectedTradingDate, String state, Summary report) {}
    public record Generation(String state, LocalDate tradingDate, Long reportId, List<String> reasons) {}

    private static final Map<String, String> INDICES = Map.of("SPX", "S&P 500", "IXIC", "NASDAQ", "RUT", "Russell 2000");
    private static final List<String> INDEX_ORDER = List.of("SPX", "IXIC", "RUT");
    private static final List<String> SECTORS = List.of("XLK", "XLF", "XLE", "XLV", "XLI", "XLY", "XLP", "XLU", "XLB", "XLRE", "XLC");
    private static final Map<String, String> MACRO = Map.of("VIX", "VIX", "DXY", "달러 지수", "US10Y", "미국 10년 금리", "GOLD", "금 선물", "WTI", "WTI 선물");
    private final MarketReportRepository repository;
    private final MarketReportStore store;
    private final CollectorClient collector;
    private final MarketIndexHistoryService prices;
    private final SectorPerformanceService sectors;
    private final MarketBreadthService breadth;
    private final NewsService news;
    private final PutCallRatioService putCall;
    private final ObjectMapper mapper;

    public MarketReportService(MarketReportRepository repository, MarketReportStore store, CollectorClient collector,
            MarketIndexHistoryService prices, SectorPerformanceService sectors, MarketBreadthService breadth,
            NewsService news, PutCallRatioService putCall, ObjectMapper mapper) {
        this.repository = repository; this.store = store; this.collector = collector; this.prices = prices;
        this.sectors = sectors; this.breadth = breadth; this.news = news; this.putCall = putCall; this.mapper = mapper;
    }

    public List<Summary> list(LocalDate before) {
        return repository.findByMarketAndTradingDateLessThanEqualOrderByTradingDateDescVersionDesc(
                "US", before == null ? LocalDate.now(ZoneOffset.UTC) : before, PageRequest.of(0, 30)).stream()
                .map(MarketReportService::summary).toList();
    }

    public Detail get(long id) {
        var row = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("시황 보고서를 찾을 수 없습니다."));
        return new Detail(summary(row), mapper.readValue(row.getPayloadJson(), Payload.class));
    }

    public Latest latest() {
        var session = session();
        var row = repository.findTopByMarketOrderByTradingDateDescVersionDesc("US").map(MarketReportService::summary).orElse(null);
        return new Latest(session == null ? null : session.tradingDate(), session == null ? "UNAVAILABLE"
                : row != null && row.tradingDate().equals(session.tradingDate()) ? "CURRENT" : "DELAYED", row);
    }

    public Generation generate() {
        Instant started = Instant.now();
        var session = session();
        if (session == null) return new Generation("DELAYED", null, null, List.of("미국장 완료 거래일을 확인하지 못했습니다."));
        var existing = repository.findByMarketAndTradingDateAndVersion("US", session.tradingDate(), 1);
        if (existing.isPresent()) return result("EXISTING", existing.get());
        List<String> required = new ArrayList<>();
        List<Index> indexRows = new ArrayList<>();
        for (String slug : INDEX_ORDER) {
            List<MarketIndexCandle> history = read(() -> prices.getHistory(slug), List.of());
            var row = index(slug, INDICES.get(slug), "지수", history, session, true);
            if (row == null || row.changePct1d() == null) required.add(INDICES.get(slug) + "의 완료 거래일/직전 거래일 마감 자료가 준비되지 않았습니다.");
            else indexRows.add(row);
        }
        if (!required.isEmpty()) return new Generation("DELAYED", session.tradingDate(), null, List.copyOf(required));

        List<String> checks = new ArrayList<>();
        List<SectorPerformance> sectorRows = read(sectors::getLatest, List.<SectorPerformance>of()).stream()
                .filter(s -> SECTORS.contains(s.slug()) && session.tradingDate().toString().equals(s.tradingDate())
                        && "XNYS".equals(s.calendar()) && s.source() != null && s.fetchedAt() != null
                        && afterClose(s.fetchedAt(), session.closedAt())).toList();
        if (sectorRows.stream().map(SectorPerformance::slug).distinct().count() != 11
                || sectorRows.stream().anyMatch(s -> !finite(s.changePct1d()) || !finite(s.changePct1w()) || !finite(s.changePct1m())))
            checks.add("섹터 자료 일부가 누락되었거나 기준 거래일과 다릅니다. 확인된 자료만 표시합니다.");
        var breadthRow = read(breadth::getLatest, (MarketBreadthResponse) null);
        if (breadthRow == null || !session.tradingDate().equals(breadthRow.tradingDate())) {
            breadthRow = null; checks.add("같은 거래일의 시장 폭 자료를 확보하지 못했습니다.");
        } else if (!"READY".equals(breadthRow.status())) checks.add("시장 폭은 일부 종목만 포함한 집계입니다.");
        if (breadthRow != null) checks.add("시장 폭 원천 일봉의 공급자·수집 시각은 검증되지 않았습니다.");

        List<Index> macroRows = new ArrayList<>();
        for (String slug : List.of("VIX", "DXY", "US10Y", "GOLD", "WTI")) {
            var row = index(slug, MACRO.get(slug), slug.equals("US10Y") ? "%" : Set.of("GOLD", "WTI").contains(slug) ? "USD" : "지수",
                    read(() -> prices.getHistory(slug), List.of()), session, false);
            if (row != null) macroRows.add(row);
        }
        checks.add("거시·심리 지표는 미국 주식장 마감 확정과 별도로 관측된 참고 자료입니다. 각 기준 시각을 확인하세요.");
        if (macroRows.size() != 5) checks.add("거시 지표 일부를 확보하지 못했습니다.");
        var fear = read(() -> collector.getFearGreed().orElse(null), (FearGreedResponse) null);
        var ratio = read(putCall::getLatestTrend, (PutCallRatioTrendResponse) null);
        if (fear == null) checks.add("공포·탐욕 지수를 확보하지 못했습니다.");
        if (ratio == null) checks.add("Put/Call 자료를 확보하지 못했습니다.");
        var headlines = headlines(read(news::getGeneralNews, (List<NewsItem>) null), session, checks);
        List<Highlight> highlights = new ArrayList<>();
        for (var row : indexRows) highlights.add(new Highlight(String.format(Locale.ROOT, "%s %.2f · 전 거래일 대비 %+.2f%%",
                row.name(), row.close(), row.changePct1d()), "indices"));
        sectorRows.stream().filter(s -> finite(s.changePct1d())).max(Comparator.comparing(SectorPerformance::changePct1d))
                .ifPresent(s -> highlights.add(new Highlight(String.format(Locale.ROOT, "확보한 섹터 중 1일 수익률 상위: %s (%+.2f%%)", s.name(), s.changePct1d()), "sectors")));
        var payload = new Payload(1, "market-report-v1", new Session(session.tradingDate(), session.closedAt(), session.calendar(), session.calendarVersion()),
                started, Instant.now(), List.copyOf(highlights), List.copyOf(indexRows), sectorRows, breadthRow,
                List.copyOf(macroRows), fear, ratio, headlines, List.copyOf(checks));
        try { return result("PUBLISHED", store.insert(payload)); }
        catch (DataIntegrityViolationException conflict) {
            // Another instance may have committed the same immutable session while capture ran.
            var winner = repository.findByMarketAndTradingDateAndVersion("US", session.tradingDate(), 1);
            if (winner.isPresent()) return result("EXISTING", winner.get());
            throw conflict;
        }
    }

    private CompletedMarketSession session() {
        var s = collector.getCompletedMarketSession().orElse(null);
        Instant now = Instant.now();
        if (s == null || !"XNYS".equals(s.calendar()) || s.tradingDate() == null || s.closedAt() == null
                || s.closedAt().isAfter(now) || s.evaluatedAt() == null || s.evaluatedAt().isBefore(now.minusSeconds(300))
                || s.evaluatedAt().isAfter(now.plusSeconds(30)) || s.sessionDates() == null || s.sessionDates().size() < 2
                || !s.tradingDate().equals(s.sessionDates().get(s.sessionDates().size() - 1))) return null;
        return s;
    }

    private static Index index(String slug, String name, String unit, List<MarketIndexCandle> history,
            CompletedMarketSession session, boolean required) {
        Map<String, MarketIndexCandle> rows = new TreeMap<>();
        for (var c : history) {
            if (c.tradingDate() == null || !c.tradingDate().matches("\\d{4}-\\d{2}-\\d{2}")
                    || c.tradingDate().compareTo(session.tradingDate().toString()) > 0 || !Double.isFinite(c.close()) || c.close() <= 0
                    || c.source() == null || c.fetchedAt() == null) continue;
            if (required && (!"COMPLETED".equals(c.sessionStatus()) || !"XNYS".equals(c.calendar()))) continue;
            rows.put(c.tradingDate(), c);
        }
        if (rows.isEmpty()) return null;
        var current = required ? rows.get(session.tradingDate().toString()) : rows.get(Collections.max(rows.keySet()));
        if (current == null || (required && !afterClose(current.fetchedAt(), session.closedAt()))) return null;
        int end = session.sessionDates().indexOf(LocalDate.parse(current.tradingDate()));
        return new Index(slug, name, unit, current.close(), change(rows, session.sessionDates(), end, 1, current.close()),
                change(rows, session.sessionDates(), end, 5, current.close()), change(rows, session.sessionDates(), end, 21, current.close()), current);
    }

    private static Double change(Map<String, MarketIndexCandle> rows, List<LocalDate> dates, int end, int offset, double close) {
        if (end < offset) return null;
        var base = rows.get(dates.get(end - offset).toString());
        if (base == null) return null;
        double result = (close / base.close() - 1) * 100;
        return Double.isFinite(result) ? result : null;
    }
    private static boolean afterClose(String value, Instant close) {
        try { var at = Instant.parse(value); return !at.isBefore(close) && !at.isAfter(Instant.now().plusSeconds(30)); }
        catch (DateTimeException e) { return false; }
    }
    private static boolean finite(Double value) { return value != null && Double.isFinite(value); }
    private static List<Headline> headlines(List<NewsItem> items, CompletedMarketSession session, List<String> checks) {
        if (items == null) { checks.add("뉴스 조회에 실패했습니다."); return List.of(); }
        // Includes the interval since the previous session date, through the target close only.
        Instant start = session.sessionDates().get(session.sessionDates().size() - 2)
                .atStartOfDay(ZoneId.of("America/New_York")).toInstant();
        Set<String> titles = new HashSet<>(); Set<String> urls = new HashSet<>();
        List<Headline> result = new ArrayList<>();
        for (var n : items.stream().sorted(Comparator.comparingLong(NewsItem::datetime).reversed()).toList()) {
            Instant at;
            try { at = Instant.ofEpochSecond(n.datetime()); } catch (DateTimeException ex) { continue; }
            if (at.isBefore(start) || at.isAfter(session.closedAt()) || n.headline() == null || n.headline().isBlank() || !safeUrl(n.url())) continue;
            String title = n.headline().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (titles.contains(title) || urls.contains(n.url())) continue;
            titles.add(title); urls.add(n.url());
            result.add(new Headline(n.headline(), n.source(), at, n.url()));
            if (result.size() == 10) break;
        }
        if (result.isEmpty()) checks.add("대상 기간에 해당하는 뉴스가 없습니다.");
        return List.copyOf(result);
    }
    private static boolean safeUrl(String value) {
        try { var u = URI.create(value); return Set.of("http", "https").contains(u.getScheme()) && u.getHost() != null; }
        catch (RuntimeException ex) { return false; }
    }
    private static <T> T read(Supplier<T> supplier, T fallback) {
        try { var result = supplier.get(); return result == null ? fallback : result; }
        catch (RuntimeException ex) { return fallback; }
    }
    private static Summary summary(MarketReport r) { return new Summary(r.getId(), r.getMarket(), r.getTradingDate(), r.getVersion(), r.getStatus(), r.getPublishedAt()); }
    private static Generation result(String state, MarketReport r) { return new Generation(state, r.getTradingDate(), r.getId(), List.of()); }
}
