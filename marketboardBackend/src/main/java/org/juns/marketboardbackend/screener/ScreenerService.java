package org.juns.marketboardbackend.screener;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.juns.marketboardbackend.collector.MomentumScreenerCandidate;
import org.juns.marketboardbackend.collector.MomentumScreenerRequest;
import org.juns.marketboardbackend.collector.MomentumScreenerResult;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.screener.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ScreenerService {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;

    public ScreenerService(JdbcTemplate jdbc, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    public ScreenerSearchResponse search(ScreenerSearchRequest request) {
        Timer.Sample total = Timer.start(metrics);
        try {
            SnapshotRun run = latestCompletedRun();
            List<Object> args = new ArrayList<>();
            args.add(run.id());
            StringBuilder where = new StringBuilder(" WHERE ss.snapshot_run_id=?");
            add(where, args, momentumColumn(request.momentumPeriod()) + ">=?", request.minMomentumPct());
            add(where, args, "ss.rsi_14<=?", request.maxRsi());
            add(where, args, "ss.above_sma_200=?", request.aboveSma200());
            add(where, args, "ss.market_cap>=?", request.minMarketCap());
            add(where, args, "ss.revenue_ttm>=?", request.minRevenue());
            add(where, args, "ss.revenue_growth>=?", request.minRevenueGrowth());
            add(where, args, "ss.roe>=?", request.minRoe());
            add(where, args, "ss.trailing_pe<=?", request.maxTrailingPe());
            add(where, args, "ss.news_sentiment>=?", request.minNewsSentiment());

            Timer.Sample query = Timer.start(metrics);
            long count = jdbc.queryForObject("SELECT COUNT(*) FROM stock_screening_snapshots ss" + where, Long.class, args.toArray());
            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(request.size());
            pageArgs.add((long) request.page() * request.size());
            String sql = "SELECT s.ticker,ss.* FROM stock_screening_snapshots ss JOIN symbols s ON s.id=ss.symbol_id"
                    + where + " ORDER BY " + sortColumn(request.sort().field()) + " "
                    + request.sort().direction().name() + ",s.ticker ASC LIMIT ? OFFSET ?";
            List<ScreenerSnapshotItem> items = jdbc.query(sql, this::mapItem, pageArgs.toArray());
            query.stop(metrics.timer("screener.snapshot.query"));
            int pages = count == 0 ? 0 : (int) ((count + request.size() - 1) / request.size());
            return new ScreenerSearchResponse(run.snapshotDate(), run.completedAt(), count, pages, request.page(), request.size(), items);
        } finally {
            total.stop(metrics.timer("screener.snapshot.total"));
        }
    }

    /** Deprecated compatibility adapter. Calculation never runs in the request path. */
    public MomentumScreenerResult runMomentumScreener(MomentumScreenerRequest request) {
        MomentumPeriod period = switch (request.momentumWindowDays() == null ? 126 : request.momentumWindowDays()) {
            case 63 -> MomentumPeriod.THREE_MONTHS;
            case 126 -> MomentumPeriod.SIX_MONTHS;
            case 252 -> MomentumPeriod.TWELVE_MONTHS;
            default -> throw new IllegalArgumentException("Snapshot supports momentumWindowDays 63, 126, or 252 only");
        };
        int topN = Math.max(1, Math.min(request.topN(), 20));
        var response = search(new ScreenerSearchRequest(period, request.minMomentumPct(), request.maxRsi(), null,
                request.minMarketCap(), request.minRevenue(), null, null, null, null,
                new ScreenerSort(sortFor(period), SortDirection.DESC), 0, topN));
        int trendWindow = request.trendMaWindow() == null ? 200 : request.trendMaWindow();
        var results = response.items().stream().map(item -> new MomentumScreenerCandidate(
                item.ticker(), momentum(item, period), item.volatility20d(), item.price().compareTo(sma(item, trendWindow)) > 0,
                item.rsi14(), item.revenueGrowth(), item.roe(), item.profitMargin(), item.trailingPe(), item.marketCap(),
                item.revenueTtm(), item.newsSentiment(), item.newsCount())).toList();
        int count = Math.toIntExact(response.totalElements());
        return new MomentumScreenerResult(count, count, count, results);
    }

    private SnapshotRun latestCompletedRun() {
        var runs = jdbc.query("SELECT id,snapshot_date,completed_at FROM screening_snapshot_runs "
                        + "WHERE status='COMPLETED' ORDER BY id DESC LIMIT 1",
                (rs, row) -> new SnapshotRun(rs.getLong("id"), rs.getDate("snapshot_date").toLocalDate(),
                        rs.getTimestamp("completed_at").toInstant()));
        if (runs.isEmpty()) throw new ResourceNotFoundException("완료된 스크리너 스냅샷이 없습니다");
        return runs.get(0);
    }

    private static void add(StringBuilder where, List<Object> args, String clause, Object value) {
        if (value != null) { where.append(" AND ").append(clause); args.add(value); }
    }

    private ScreenerSnapshotItem mapItem(ResultSet rs, int row) throws SQLException {
        return new ScreenerSnapshotItem(rs.getString("ticker"), rs.getBigDecimal("price"),
                rs.getBigDecimal("momentum_3m"), rs.getBigDecimal("momentum_6m"), rs.getBigDecimal("momentum_12m"),
                rs.getBigDecimal("volatility_20d"), rs.getBigDecimal("rsi_14"), rs.getBigDecimal("sma_50"),
                rs.getBigDecimal("sma_100"), rs.getBigDecimal("sma_200"), (Boolean) rs.getObject("above_sma_200"),
                rs.getBigDecimal("market_cap"), rs.getBigDecimal("revenue_ttm"), rs.getBigDecimal("revenue_growth"),
                rs.getBigDecimal("roe"), rs.getBigDecimal("profit_margin"), rs.getBigDecimal("trailing_pe"),
                rs.getBigDecimal("news_sentiment"), rs.getInt("news_count"));
    }

    private static String momentumColumn(MomentumPeriod period) { return switch (period) {
        case THREE_MONTHS -> "ss.momentum_3m"; case SIX_MONTHS -> "ss.momentum_6m"; case TWELVE_MONTHS -> "ss.momentum_12m"; }; }
    private static ScreenerSortField sortFor(MomentumPeriod period) { return switch (period) {
        case THREE_MONTHS -> ScreenerSortField.MOMENTUM_3M; case SIX_MONTHS -> ScreenerSortField.MOMENTUM_6M; case TWELVE_MONTHS -> ScreenerSortField.MOMENTUM_12M; }; }
    private static String sortColumn(ScreenerSortField field) { return switch (field) {
        case MOMENTUM_3M -> "ss.momentum_3m"; case MOMENTUM_6M -> "ss.momentum_6m"; case MOMENTUM_12M -> "ss.momentum_12m";
        case RSI_14 -> "ss.rsi_14"; case VOLATILITY_20D -> "ss.volatility_20d"; case MARKET_CAP -> "ss.market_cap";
        case REVENUE_GROWTH -> "ss.revenue_growth"; case ROE -> "ss.roe"; case TRAILING_PE -> "ss.trailing_pe";
        case NEWS_SENTIMENT -> "ss.news_sentiment"; case TICKER -> "s.ticker"; }; }
    private static BigDecimal momentum(ScreenerSnapshotItem item, MomentumPeriod period) { return switch (period) {
        case THREE_MONTHS -> item.momentum3m(); case SIX_MONTHS -> item.momentum6m(); case TWELVE_MONTHS -> item.momentum12m(); }; }
    private static BigDecimal sma(ScreenerSnapshotItem item, int window) { return switch (window) {
        case 50 -> item.sma50(); case 100 -> item.sma100(); case 200 -> item.sma200();
        default -> throw new IllegalArgumentException("Snapshot supports trendMaWindow 50, 100, or 200 only"); }; }
    private record SnapshotRun(long id, LocalDate snapshotDate, Instant completedAt) {}
}
