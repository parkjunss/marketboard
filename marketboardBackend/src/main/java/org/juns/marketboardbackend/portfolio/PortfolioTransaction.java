package org.juns.marketboardbackend.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.juns.marketboardbackend.symbol.Symbol;

@Entity
@Table(name = "portfolio_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_portfolio_transactions_source",
                        columnNames = {"portfolio_id", "source_transaction_key"}),
                @UniqueConstraint(name = "uk_portfolio_transactions_opening",
                        columnNames = {"portfolio_id", "source_position_id"}),
                @UniqueConstraint(name = "uk_portfolio_transactions_reversal",
                        columnNames = "reversal_of_transaction_id")
        },
        indexes = {
                @Index(name = "idx_portfolio_transactions_time",
                        columnList = "portfolio_id,occurred_at,id"),
                @Index(name = "idx_portfolio_transactions_symbol",
                        columnList = "portfolio_id,symbol_id,occurred_at,id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioTransaction {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false, updatable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "symbol_id", nullable = false, updatable = false)
    private Symbol symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 32)
    private PortfolioTransactionType transactionType;

    @Column(precision = 18, scale = 6, updatable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 18, scale = 4, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal fee;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "source_transaction_key", updatable = false, length = 128)
    private String sourceTransactionKey;

    @Column(name = "source_position_id", updatable = false)
    private Long sourcePositionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_transaction_id", updatable = false)
    private PortfolioTransaction reversalOf;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private PortfolioTransaction(Portfolio portfolio, Symbol symbol, PortfolioTransactionType transactionType,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, Instant occurredAt,
            String sourceTransactionKey, Long sourcePositionId, PortfolioTransaction reversalOf) {
        this.portfolio = require(portfolio, "포트폴리오");
        this.symbol = require(symbol, "종목");
        this.transactionType = require(transactionType, "거래 유형");
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fee = requireDecimal(requireNonNegative(fee, "수수료"), "수수료", 18, 4);
        this.currency = "USD";
        this.occurredAt = require(occurredAt, "거래 시각");
        this.sourceTransactionKey = normalizeKey(sourceTransactionKey);
        this.sourcePositionId = sourcePositionId;
        this.reversalOf = reversalOf;
        this.createdAt = Instant.now();
    }

    public static PortfolioTransaction openingBalance(Portfolio portfolio, Symbol symbol, BigDecimal quantity,
            BigDecimal unitPrice, Long sourcePositionId, Instant occurredAt) {
        if (sourcePositionId == null || sourcePositionId <= 0) {
            throw new IllegalArgumentException("기초잔고 원본 포지션이 필요합니다.");
        }
        return new PortfolioTransaction(portfolio, symbol, PortfolioTransactionType.OPENING_BALANCE,
                quantity(quantity), money(unitPrice, "단가"), ZERO,
                occurredAt, null, sourcePositionId, null);
    }

    public static PortfolioTransaction buy(Portfolio portfolio, Symbol symbol, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal fee, Instant occurredAt, String sourceTransactionKey) {
        return trade(portfolio, symbol, PortfolioTransactionType.BUY, quantity, unitPrice, fee,
                occurredAt, sourceTransactionKey);
    }

    public static PortfolioTransaction sell(Portfolio portfolio, Symbol symbol, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal fee, Instant occurredAt, String sourceTransactionKey) {
        return trade(portfolio, symbol, PortfolioTransactionType.SELL, quantity, unitPrice, fee,
                occurredAt, sourceTransactionKey);
    }

    public static PortfolioTransaction reversal(Portfolio portfolio, Symbol symbol,
            PortfolioTransaction original, Instant occurredAt) {
        require(original, "원거래");
        if (original.transactionType == PortfolioTransactionType.REVERSAL) {
            throw new IllegalArgumentException("정정 거래를 다시 정정할 수 없습니다.");
        }
        if (!sameEntity(original.portfolio, portfolio) || !sameEntity(original.symbol, symbol)) {
            throw new IllegalArgumentException("정정 거래는 원거래와 같은 포트폴리오와 종목이어야 합니다.");
        }
        return new PortfolioTransaction(portfolio, symbol, PortfolioTransactionType.REVERSAL,
                null, null, ZERO, occurredAt, null, null, original);
    }

    private static PortfolioTransaction trade(Portfolio portfolio, Symbol symbol,
            PortfolioTransactionType type, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee,
            Instant occurredAt, String sourceTransactionKey) {
        return new PortfolioTransaction(portfolio, symbol, type,
                quantity(quantity), money(unitPrice, "단가"), fee,
                occurredAt, sourceTransactionKey, null, null);
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + "이(가) 필요합니다.");
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + "은(는) 양수여야 합니다.");
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + "은(는) 0 이상이어야 합니다.");
        return value;
    }

    private static BigDecimal quantity(BigDecimal value) {
        return requireDecimal(requirePositive(value, "수량"), "수량", 18, 6);
    }

    private static BigDecimal money(BigDecimal value, String name) {
        return requireDecimal(requirePositive(value, name), name, 18, 4);
    }

    private static BigDecimal requireDecimal(BigDecimal value, String name, int precision, int scale) {
        final BigDecimal scaled;
        try {
            scaled = value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + "의 소수점 자릿수는 " + scale + "자리 이하여야 합니다.");
        }
        if (scaled.precision() > precision) {
            throw new IllegalArgumentException(name + "이(가) 저장 범위를 초과했습니다.");
        }
        return scaled;
    }

    private static boolean sameEntity(Portfolio left, Portfolio right) {
        return left == right || left != null && right != null
                && left.getId() != null && left.getId() > 0 && left.getId().equals(right.getId());
    }

    private static boolean sameEntity(Symbol left, Symbol right) {
        return left == right || left != null && right != null
                && left.getId() != null && left.getId() > 0 && left.getId().equals(right.getId());
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 128) throw new IllegalArgumentException("원본 거래 키는 128자 이하여야 합니다.");
        return normalized;
    }
}
