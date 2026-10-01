package org.juns.marketboardbackend.screener.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ScreenerSearchResponse(
        LocalDate snapshotDate, Instant calculatedAt, long totalElements, int totalPages,
        int page, int size, List<ScreenerSnapshotItem> items) {}
