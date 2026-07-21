package org.juns.marketboardbackend.marketbreadth.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.juns.marketboardbackend.marketbreadth.MarketBreadthSnapshot;

public record MarketBreadthResponse(
        LocalDate snapshotDate,
        int advancingCount,
        int decliningCount,
        int unchangedCount,
        int new52wHighCount,
        int new52wLowCount,
        int universeSize,
        Instant computedAt) {

    public static MarketBreadthResponse from(MarketBreadthSnapshot snapshot) {
        return new MarketBreadthResponse(
                snapshot.getSnapshotDate(),
                snapshot.getAdvancingCount(),
                snapshot.getDecliningCount(),
                snapshot.getUnchangedCount(),
                snapshot.getNew52wHighCount(),
                snapshot.getNew52wLowCount(),
                snapshot.getUniverseSize(),
                snapshot.getComputedAt());
    }
}
