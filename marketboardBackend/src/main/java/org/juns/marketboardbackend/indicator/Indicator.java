package org.juns.marketboardbackend.indicator;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "indicators")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Indicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id", nullable = false)
    private Symbol symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 20)
    private IndicatorType indicatorType;

    @Column(nullable = false, length = 10)
    private String timeframe;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal value;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Builder
    public Indicator(Symbol symbol, IndicatorType indicatorType, String timeframe, BigDecimal value) {
        this.symbol = symbol;
        this.indicatorType = indicatorType;
        this.timeframe = timeframe;
        this.value = value;
        this.computedAt = Instant.now();
    }

    public void updateValue(BigDecimal value) {
        this.value = value;
        this.computedAt = Instant.now();
    }
}
