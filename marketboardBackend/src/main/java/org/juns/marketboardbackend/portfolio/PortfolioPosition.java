package org.juns.marketboardbackend.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.juns.marketboardbackend.symbol.Symbol;

/**
 * A per-symbol snapshot within a {@link Portfolio}: current quantity and average cost, entered
 * directly by the user. There is no buy/sell transaction ledger — only unrealized P/L (current
 * price vs. avg_cost) is derivable from this table, not realized gains.
 */
@Entity
@Table(name = "portfolio_positions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id", nullable = false)
    private Symbol symbol;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgCost;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public PortfolioPosition(Portfolio portfolio, Symbol symbol, BigDecimal quantity, BigDecimal avgCost) {
        this.portfolio = portfolio;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgCost = avgCost;
        this.updatedAt = Instant.now();
    }

    public void update(BigDecimal quantity, BigDecimal avgCost) {
        this.quantity = quantity;
        this.avgCost = avgCost;
        this.updatedAt = Instant.now();
    }
}
