package org.juns.marketboardbackend.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_weight_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioWeightRule {

    @Id
    @Column(name = "portfolio_id")
    private Long portfolioId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "portfolio_id")
    private Portfolio portfolio;

    @Column(name = "max_position_weight", nullable = false, precision = 7, scale = 6)
    private BigDecimal maxPositionWeight;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PortfolioWeightRule(Portfolio portfolio, BigDecimal maxPositionWeight) {
        this.portfolio = portfolio;
        update(maxPositionWeight);
    }

    public void update(BigDecimal maxPositionWeight) {
        this.maxPositionWeight = maxPositionWeight;
        this.updatedAt = Instant.now();
    }
}
