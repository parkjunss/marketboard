package org.juns.marketboardbackend.portfolio.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.juns.marketboardbackend.portfolio.Portfolio;

public record PortfolioSummaryResponse(
        Long id,
        String name,
        int positionCount,
        BigDecimal totalMarketValue,
        BigDecimal totalCostBasis,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalUnrealizedPnlPct,
        Instant createdAt,
        Instant updatedAt,
        int pricedPositionCount,
        int unpricedPositionCount,
        int stalePositionCount,
        int unverifiedPositionCount,
        String valuationStatus) {

    /**
     * Totals are summed only over positions with a resolvable price (see {@link PortfolioPositionResponse}),
     * so totalCostBasis stays in lockstep with totalMarketValue instead of mixing in unpriced positions —
     * null when the portfolio has no priced positions rather than a misleading partial total.
     */
    public static PortfolioSummaryResponse of(Portfolio portfolio, List<PortfolioPositionResponse> positions) {
        BigDecimal totalMarketValue = null;
        BigDecimal totalCostBasis = null;
        for (PortfolioPositionResponse position : positions) {
            if (position.marketValue() == null) continue;
            totalMarketValue = (totalMarketValue == null ? BigDecimal.ZERO : totalMarketValue).add(position.marketValue());
            totalCostBasis = (totalCostBasis == null ? BigDecimal.ZERO : totalCostBasis).add(position.costBasis());
        }
        BigDecimal totalUnrealizedPnl = totalMarketValue != null ? totalMarketValue.subtract(totalCostBasis) : null;
        BigDecimal totalUnrealizedPnlPct = totalUnrealizedPnl != null && totalCostBasis.signum() != 0
                ? totalUnrealizedPnl.divide(totalCostBasis, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : null;

        int priced = (int) positions.stream().filter(p -> p.marketValue() != null).count();
        int stale = (int) positions.stream().filter(p -> "STALE".equals(p.priceStatus())).count();
        int unverified = (int) positions.stream().filter(p -> "UNVERIFIED".equals(p.priceStatus())).count();
        String status = positions.isEmpty() ? "EMPTY" : priced == 0 ? "UNAVAILABLE"
                : priced < positions.size() ? "PARTIAL" : stale > 0 || unverified > 0 ? "UNVERIFIED" : "READY";
        return new PortfolioSummaryResponse(
                portfolio.getId(),
                portfolio.getName(),
                positions.size(),
                totalMarketValue,
                totalCostBasis,
                totalUnrealizedPnl,
                totalUnrealizedPnlPct,
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt(), priced, positions.size() - priced, stale, unverified, status);
    }
}
