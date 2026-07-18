package org.juns.marketboardbackend.quote;

import java.math.BigDecimal;

/** A best-effort current price: live Redis tick when available, else the latest daily close. */
public record ResolvedPrice(BigDecimal price, boolean isLive) {
}
