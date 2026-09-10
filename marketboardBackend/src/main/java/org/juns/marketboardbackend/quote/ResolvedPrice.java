package org.juns.marketboardbackend.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A best-effort current price: live Redis tick when available, else the latest daily close. */
public record ResolvedPrice(BigDecimal price, String source, String provider, Instant asOf,
                            Instant fetchedAt, LocalDate sessionDate, String status) {
    public boolean isLive() {
        return "LIVE".equals(source) && "RECENT".equals(status);
    }
}
