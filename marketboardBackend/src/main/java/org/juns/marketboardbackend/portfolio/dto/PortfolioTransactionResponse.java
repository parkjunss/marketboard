package org.juns.marketboardbackend.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import org.juns.marketboardbackend.portfolio.PortfolioTransaction;
import org.juns.marketboardbackend.portfolio.PortfolioTransactionType;

public record PortfolioTransactionResponse(
        Long id,
        Long portfolioId,
        String ticker,
        PortfolioTransactionType type,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal fee,
        String currency,
        Instant occurredAt,
        Instant createdAt) {

    public static PortfolioTransactionResponse from(PortfolioTransaction transaction) {
        return new PortfolioTransactionResponse(
                transaction.getId(), transaction.getPortfolio().getId(),
                transaction.getSymbol().getTicker(), transaction.getTransactionType(),
                transaction.getQuantity(), transaction.getUnitPrice(), transaction.getFee(),
                transaction.getCurrency(), transaction.getOccurredAt(), transaction.getCreatedAt());
    }
}
