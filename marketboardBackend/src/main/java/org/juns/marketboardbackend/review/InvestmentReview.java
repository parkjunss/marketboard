package org.juns.marketboardbackend.review;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "investment_reviews")
public class InvestmentReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(nullable = false, updatable = false) private int period;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT") private String payloadJson;
    protected InvestmentReview() {}
    public InvestmentReview(Long userId, int period, String payloadJson) {
        this.userId = userId; this.period = period; this.payloadJson = payloadJson; this.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public int getPeriod() { return period; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPayloadJson() { return payloadJson; }
}
