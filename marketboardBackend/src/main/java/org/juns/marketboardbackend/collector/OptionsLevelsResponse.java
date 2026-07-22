package org.juns.marketboardbackend.collector;

import java.util.List;

/**
 * Max pain + open-interest-ranked support/resistance candidates for the nearest options
 * expiration, sourced from CBOE's public delayed quotes (not yfinance -- see the collector's
 * app/options_levels.py for why). spotPrice/maxPain are nullable: the collector can resolve
 * strikes without a spot price in principle, and maxPain is null if a ticker somehow has no
 * strikes on either side.
 */
public record OptionsLevelsResponse(
        String ticker,
        String expiration,
        Double spotPrice,
        Double maxPain,
        List<OptionsLevel> resistanceLevels,
        List<OptionsLevel> supportLevels) {
}
