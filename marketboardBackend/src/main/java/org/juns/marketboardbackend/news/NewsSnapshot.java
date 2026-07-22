package org.juns.marketboardbackend.news;

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

/**
 * A single global row holding the most recently fetched general news feed, refreshed by
 * {@link NewsService} on a schedule rather than per-request -- see that class for why.
 */
@Entity
@Table(name = "news_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Builder
    public NewsSnapshot(String payloadJson) {
        this.payloadJson = payloadJson;
        this.computedAt = Instant.now();
    }

    public void update(String payloadJson) {
        this.payloadJson = payloadJson;
        this.computedAt = Instant.now();
    }
}
