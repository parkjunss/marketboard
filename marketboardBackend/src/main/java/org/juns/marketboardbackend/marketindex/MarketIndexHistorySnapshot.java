package org.juns.marketboardbackend.marketindex;

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
 * A cached copy of one macro index's daily OHLC history, refreshed on a schedule by
 * {@link MarketIndexHistoryService} -- see that class for why.
 */
@Entity
@Table(name = "market_index_history_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketIndexHistorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String slug;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Builder
    public MarketIndexHistorySnapshot(String slug, String payloadJson) {
        this.slug = slug;
        this.payloadJson = payloadJson;
        this.computedAt = Instant.now();
    }

    public void update(String payloadJson) {
        this.payloadJson = payloadJson;
        this.computedAt = Instant.now();
    }
}
