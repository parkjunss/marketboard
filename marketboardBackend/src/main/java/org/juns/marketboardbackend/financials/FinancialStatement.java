package org.juns.marketboardbackend.financials;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A cached copy of one ticker's yfinance fundamentals, refreshed lazily on request (see FinancialsService). */
@Entity
@Table(name = "financial_statements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker", nullable = false, unique = true, length = 20)
    private String ticker;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Builder
    public FinancialStatement(String ticker, String payloadJson) {
        this.ticker = ticker;
        this.payloadJson = payloadJson;
        this.fetchedAt = Instant.now();
    }

    public void update(String payloadJson) {
        this.payloadJson = payloadJson;
        this.fetchedAt = Instant.now();
    }
}
