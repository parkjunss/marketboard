package org.juns.marketboardbackend.portfolio.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        BigDecimal unrealizedPnlPct) {

    /** priceSource: LIVE (real-time WS tick), CLOSE (latest daily bar), or UNAVAILABLE (neither). */
    public static PortfolioPositionResponse from(PortfolioPosition position, ResolvedPrice resolvedPrice) {
        BigDecimal quantity = position.getQuantity();
        BigDecimal avgCost = position.getAvgCost();
        BigDecimal costBasis = avgCost.multiply(quantity);

        BigDecimal currentPrice = resolvedPrice != null ? resolvedPrice.price() : null;
        String priceSource = resolvedPrice == null ? "UNAVAILABLE" : resolvedPrice.isLive() ? "LIVE" : "CLOSE";
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
                unrealizedPnlPct);
    }
}
