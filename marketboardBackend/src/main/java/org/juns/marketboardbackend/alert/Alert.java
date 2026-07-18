package org.juns.marketboardbackend.alert;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.user.User;

@Entity
@Table(name = "alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol_id", nullable = false)
    private Symbol symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private AlertCondition condition;

    @Column(name = "target_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal targetPrice;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public Alert(User user, Symbol symbol, AlertCondition condition, BigDecimal targetPrice) {
        this.user = user;
        this.symbol = symbol;
        this.condition = condition;
        this.targetPrice = targetPrice;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void markTriggered() {
        this.triggeredAt = Instant.now();
    }

    public boolean isActive() {
        return triggeredAt == null;
    }
}
