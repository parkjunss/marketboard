package org.juns.marketboardbackend.collector;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CompletedMarketSession(String calendar, String timezone, String calendarVersion,
        Instant evaluatedAt, LocalDate tradingDate, Instant closedAt, List<LocalDate> sessionDates) {}
