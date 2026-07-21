package org.juns.marketboardbackend.marketbreadth;

import java.time.Instant;
import java.time.LocalDate;
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
 * One day's market-breadth snapshot (advance/decline + 52-week high/low counts) over the S&P 500
 * + active-symbol universe. Recomputed once a day (see MarketBreadthService) since it's derived
 * purely from daily bars, which only change once a day themselves.
 */
@Entity
@Table(name = "market_breadth_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketBreadthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "advancing_count", nullable = false)
    private int advancingCount;

    @Column(name = "declining_count", nullable = false)
    private int decliningCount;

    @Column(name = "unchanged_count", nullable = false)
    private int unchangedCount;

    @Column(name = "new_52w_high_count", nullable = false)
    private int new52wHighCount;

    @Column(name = "new_52w_low_count", nullable = false)
    private int new52wLowCount;

    @Column(name = "universe_size", nullable = false)
    private int universeSize;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Builder
    public MarketBreadthSnapshot(
            LocalDate snapshotDate,
            int advancingCount,
            int decliningCount,
            int unchangedCount,
            int new52wHighCount,
            int new52wLowCount,
            int universeSize) {
        this.snapshotDate = snapshotDate;
        this.advancingCount = advancingCount;
        this.decliningCount = decliningCount;
        this.unchangedCount = unchangedCount;
        this.new52wHighCount = new52wHighCount;
        this.new52wLowCount = new52wLowCount;
        this.universeSize = universeSize;
        this.computedAt = Instant.now();
    }

    public void update(
            int advancingCount, int decliningCount, int unchangedCount, int new52wHighCount, int new52wLowCount, int universeSize) {
        this.advancingCount = advancingCount;
        this.decliningCount = decliningCount;
        this.unchangedCount = unchangedCount;
        this.new52wHighCount = new52wHighCount;
        this.new52wLowCount = new52wLowCount;
        this.universeSize = universeSize;
        this.computedAt = Instant.now();
    }
}
