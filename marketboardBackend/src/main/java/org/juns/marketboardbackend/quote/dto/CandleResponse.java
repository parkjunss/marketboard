package org.juns.marketboardbackend.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;
import org.juns.marketboardbackend.pricehistory.PriceHistory;

public record CandleResponse(Instant ts, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, Long volume) {

    public static CandleResponse from(PriceHistory candle) {
        return new CandleResponse(
                candle.getTs(), candle.getOpen(), candle.getHigh(), candle.getLow(), candle.getClose(), candle.getVolume());
    }
}
