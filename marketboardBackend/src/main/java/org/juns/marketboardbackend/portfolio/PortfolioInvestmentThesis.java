package org.juns.marketboardbackend.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.juns.marketboardbackend.symbol.Symbol;

@Entity
@Table(name = "portfolio_investment_theses", uniqueConstraints =
        @UniqueConstraint(name = "uk_portfolio_theses_revision", columnNames = {"portfolio_id", "symbol_id", "revision"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioInvestmentThesis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false, updatable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "symbol_id", nullable = false, updatable = false)
    private Symbol symbol;

    @Column(nullable = false, updatable = false)
    private int revision;

    @Column(nullable = false, updatable = false, length = 2000)
    private String thesis;

    @Column(name = "invalidation_condition", nullable = false, updatable = false, length = 2000)
    private String invalidationCondition;

    @Column(name = "target_weight", nullable = false, updatable = false, precision = 7, scale = 6)
    private BigDecimal targetWeight;

    @Column(name = "max_weight", nullable = false, updatable = false, precision = 7, scale = 6)
    private BigDecimal maxWeight;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PortfolioInvestmentThesis(Portfolio portfolio, Symbol symbol, int revision,
            String thesis, String invalidationCondition, BigDecimal targetWeight, BigDecimal maxWeight) {
        this.portfolio = portfolio;
        this.symbol = symbol;
        this.revision = revision;
        this.thesis = thesis.trim();
        this.invalidationCondition = invalidationCondition.trim();
        this.targetWeight = targetWeight;
        this.maxWeight = maxWeight;
        this.createdAt = Instant.now();
    }
}
