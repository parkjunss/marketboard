package org.juns.marketboardbackend.report;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stock_reports")
public class StockReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, updatable = false, length = 20) private String ticker;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT") private String payloadJson;
    protected StockReport() {}
    public StockReport(Long userId, String ticker, String payloadJson) {
        this.userId = userId; this.ticker = ticker; this.payloadJson = payloadJson;
        this.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPayloadJson() { return payloadJson; }
}
