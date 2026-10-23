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
        Instant computedAt,
        LocalDate tradingDate, String source, String calendar, String calendarVersion,
        Instant sessionClosedAt, Integer eligibleCount, String status) {
    public MarketBreadthResponse {
        // Also covers old Redis entries, which bypass from(snapshot) during a rolling upgrade.
        if (tradingDate == null || status == null) status = "UNVERIFIED";
    }

    public static MarketBreadthResponse from(MarketBreadthSnapshot snapshot) {
        return new MarketBreadthResponse(
                snapshot.getSnapshotDate(),
                snapshot.getAdvancingCount(),
                snapshot.getDecliningCount(),
                snapshot.getUnchangedCount(),
                snapshot.getNew52wHighCount(),
                snapshot.getNew52wLowCount(),
                snapshot.getUniverseSize(),
                snapshot.getComputedAt(), snapshot.getTradingDate(), snapshot.getSource(), snapshot.getCalendar(),
                snapshot.getCalendarVersion(), snapshot.getSessionClosedAt(), snapshot.getEligibleCount(),
                snapshot.getTradingDate() == null ? "UNVERIFIED"
                        : snapshot.getEligibleCount() != null && snapshot.getUniverseSize() < snapshot.getEligibleCount()
                                ? "PARTIAL" : "READY");
    }
}
