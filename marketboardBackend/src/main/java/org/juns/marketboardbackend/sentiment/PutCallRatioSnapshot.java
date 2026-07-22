package org.juns.marketboardbackend.sentiment;

import java.time.Instant;
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
 * A cached copy of one ticker's most recently computed put/call volume ratio -- SPY is refreshed
 * on a schedule (the market-wide sentiment card), everything else lazily on request (individual
 * stock detail pages). See {@link PutCallRatioService} for both.
 */
@Entity
@Table(name = "put_call_ratio_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PutCallRatioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String ticker;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Builder
    public PutCallRatioSnapshot(String ticker, String payloadJson) {
        this.ticker = ticker;
        this.payloadJson = payloadJson;
        this.computedAt = Instant.now();
    }

    public void update(String payloadJson) {
        this.payloadJson = payloadJson;
        this.computedAt = Instant.now();
    }
}
