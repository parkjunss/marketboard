package org.juns.marketboardbackend.collector;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketEvidenceCompatibilityTest {
    final ObjectMapper mapper = new ObjectMapper();

    @Test void legacyJsonHasUnknownEvidence() {
        var candle = mapper.readValue("""
                {"ts":"2026-03-09T04:00:00Z","open":1,"high":1,"low":1,"close":1,"volume":0}
                """, MarketIndexCandle.class);
        assertThat(candle.tradingDate()).isNull();
        assertThat(candle.sessionStatus()).isEqualTo("UNVERIFIED");
        var sector = mapper.readValue("""
                {"slug":"XLK","name":"Technology","changePct1d":1,"changePct1w":2,"changePct1m":3}
                """, SectorPerformance.class);
        assertThat(sector.source()).isNull();
        assertThat(sector.status()).isEqualTo("UNVERIFIED");
        assertThat(sector.changePct1d()).isEqualTo(1);
    }

    @Test void evidenceSurvivesSnapshotSerialization() {
        var candle = new MarketIndexCandle("2026-03-09T04:00:00Z", 1, 1, 1, 1, 0,
                "2026-03-09", "2026-03-09T21:00:00Z", "Yahoo Finance via yfinance", "^GSPC",
                "XNYS", "COMPLETED", "YFINANCE_AUTO_ADJUST");
        assertThat(mapper.readValue(mapper.writeValueAsString(candle), MarketIndexCandle.class)).isEqualTo(candle);
        var sector = new SectorPerformance("XLK", "Technology", 1.0, 2.0, 3.0, "2026-03-09", "2026-03-09",
                "2026-03-09T21:00:00Z", "Yahoo Finance via yfinance", "XNYS", "READY", "YFINANCE_AUTO_ADJUST");
        assertThat(mapper.readValue(mapper.writeValueAsString(sector), SectorPerformance.class)).isEqualTo(sector);
    }
}
