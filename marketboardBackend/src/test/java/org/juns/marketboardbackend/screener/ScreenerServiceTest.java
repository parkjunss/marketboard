package org.juns.marketboardbackend.screener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.screener.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ScreenerServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ScreenerService service = new ScreenerService(jdbc, new SimpleMeterRegistry());

    @Test
    void searchUsesLatestCompletedRunAndWhitelistedFiltersSortAndPagination() throws Exception {
        stubCompletedRun(9L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(21L);
        ResultSet item = snapshotRow();
        when(jdbc.query(startsWith("SELECT s.ticker"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1)).mapRow(item, 0)));

        var response = service.search(new ScreenerSearchRequest(
                MomentumPeriod.SIX_MONTHS, new BigDecimal("10"), new BigDecimal("70"), true,
                new BigDecimal("10000000000"), null, new BigDecimal("5"), null, new BigDecimal("30"), null,
                new ScreenerSort(ScreenerSortField.MOMENTUM_6M, SortDirection.DESC), 1, 20));

        assertThat(response.totalElements()).isEqualTo(21);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.items()).extracting(ScreenerSnapshotItem::ticker).containsExactly("AAA");
        verify(jdbc).query(contains("WHERE status='COMPLETED' ORDER BY id DESC LIMIT 1"), any(RowMapper.class));
        assertThat(mockingDetails(jdbc).getInvocations())
                .anySatisfy(invocation -> assertThat(invocation.getArguments()[0].toString())
                        .contains("ss.momentum_6m>=?", "ss.above_sma_200=?", "ss.momentum_6m DESC", "LIMIT ? OFFSET ?"));
    }

    @Test
    void searchSupportsAscendingTickerSortWithoutUsingClientSql() throws Exception {
        stubCompletedRun(10L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(startsWith("SELECT s.ticker"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.search(new ScreenerSearchRequest(null, null, null, null, null, null, null, null, null, null,
                new ScreenerSort(ScreenerSortField.TICKER, SortDirection.ASC), 0, 20));

        verify(jdbc).query(contains("ORDER BY s.ticker ASC"), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void searchFailsClearlyWhenNoCompletedSnapshotExists() {
        when(jdbc.query(startsWith("SELECT id,snapshot_date"), any(RowMapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.search(new ScreenerSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null, 0, 20)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("완료된");
    }

    private void stubCompletedRun(long id) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getDate("snapshot_date")).thenReturn(Date.valueOf(LocalDate.of(2026, 10, 1)));
        when(rs.getTimestamp("completed_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")));
        when(jdbc.query(startsWith("SELECT id,snapshot_date"), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1)).mapRow(rs, 0)));
    }

    private ResultSet snapshotRow() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ticker")).thenReturn("AAA");
        for (String column : List.of("price", "momentum_3m", "momentum_6m", "momentum_12m", "volatility_20d",
                "rsi_14", "sma_50", "sma_100", "sma_200", "market_cap", "revenue_ttm", "revenue_growth",
                "roe", "profit_margin", "trailing_pe", "news_sentiment")) {
            when(rs.getBigDecimal(column)).thenReturn(BigDecimal.ONE);
        }
        when(rs.getObject("above_sma_200")).thenReturn(true);
        when(rs.getInt("news_count")).thenReturn(1);
        return rs;
    }
}
