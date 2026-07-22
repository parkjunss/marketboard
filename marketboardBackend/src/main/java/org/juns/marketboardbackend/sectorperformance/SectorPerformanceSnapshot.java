package org.juns.marketboardbackend.sectorperformance;

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
 * A single global row holding the most recently computed sector-rotation ranking, refreshed by
 * {@link SectorPerformanceService} on a schedule rather than per-request -- see that class for why.
 */
@Entity
@Table(name = "sector_performance_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectorPerformanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Builder
    public SectorPerformanceSnapshot(String payloadJson) {
        this.payloadJson = payloadJson;
        this.computedAt = Instant.now();
    }

    public void update(String payloadJson) {
        this.payloadJson = payloadJson;
        this.computedAt = Instant.now();
    }
}
