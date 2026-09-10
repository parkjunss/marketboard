package org.juns.marketboardbackend.portfolio.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import org.juns.marketboardbackend.portfolio.PortfolioPosition;
import org.juns.marketboardbackend.quote.ResolvedPrice;

public record PortfolioPositionResponse(
        Long id,
        Long symbolId,
        String ticker,
        String name,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal currentPrice,
        String priceSource,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPct,
        String priceStatus,
        String priceProvider,
        Instant priceAsOf,
        Instant priceFetchedAt,
        LocalDate priceSessionDate,
        long version) {

    /** priceSource: LIVE (real-time WS tick), CLOSE (latest daily bar), or UNAVAILABLE (neither). */
    public static PortfolioPositionResponse from(PortfolioPosition position, ResolvedPrice resolvedPrice) {
        BigDecimal quantity = position.getQuantity();
        BigDecimal avgCost = position.getAvgCost();
        BigDecimal costBasis = avgCost.multiply(quantity);

        BigDecimal currentPrice = resolvedPrice != null ? resolvedPrice.price() : null;
        String priceSource = resolvedPrice == null ? "UNAVAILABLE" : resolvedPrice.source();
        BigDecimal marketValue = currentPrice != null ? currentPrice.multiply(quantity) : null;
        BigDecimal unrealizedPnl = marketValue != null ? marketValue.subtract(costBasis) : null;
        BigDecimal unrealizedPnlPct = unrealizedPnl != null && costBasis.signum() != 0
                ? unrealizedPnl.divide(costBasis, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : null;

        return new PortfolioPositionResponse(
                position.getId(),
                position.getSymbol().getId(),
                position.getSymbol().getTicker(),
                position.getSymbol().getName(),
                quantity,
                avgCost,
                currentPrice,
                priceSource,
                marketValue,
                costBasis,
                unrealizedPnl,
                unrealizedPnlPct,
                resolvedPrice == null ? "UNAVAILABLE" : resolvedPrice.status(),
                resolvedPrice == null ? "UNKNOWN" : resolvedPrice.provider(),
                resolvedPrice == null ? null : resolvedPrice.asOf(),
                resolvedPrice == null ? null : resolvedPrice.fetchedAt(),
                resolvedPrice == null ? null : resolvedPrice.sessionDate(), position.getVersion());
    }
}
