package org.juns.marketboardbackend.marketreport;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_reports", uniqueConstraints = @UniqueConstraint(name = "uk_market_report_session",
        columnNames = {"market", "trading_date", "report_version"}))
public class MarketReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false, length = 16) private String market;
    @Column(nullable = false, updatable = false) private LocalDate tradingDate;
    @Column(name = "report_version", nullable = false, updatable = false) private int version;
    @Column(nullable = false, updatable = false, length = 16) private String status;
    @Column(nullable = false, updatable = false) private Instant publishedAt;
    @Column(nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT") private String payloadJson;

    protected MarketReport() {}
    public MarketReport(LocalDate tradingDate, int version, String status, String payloadJson) {
        this.market = "US"; this.tradingDate = tradingDate; this.version = version;
        this.status = status; this.payloadJson = payloadJson;
        this.publishedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public String getMarket() { return market; }
    public LocalDate getTradingDate() { return tradingDate; }
    public int getVersion() { return version; }
    public String getStatus() { return status; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPayloadJson() { return payloadJson; }
}
