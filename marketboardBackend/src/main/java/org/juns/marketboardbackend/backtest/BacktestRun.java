package org.juns.marketboardbackend.backtest;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.juns.marketboardbackend.user.User;

/**
 * A saved backtest run. Phase 1 runs are computed synchronously (the request handler blocks on
 * the collector call), so PENDING is only ever a transient in-memory state before the same
 * request resolves it to DONE/FAILED -- kept as a real status (rather than just a boolean) so a
 * later phase can make heavier runs genuinely asynchronous without a schema change.
 */
@Entity
@Table(name = "backtest_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BacktestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BacktestStatus status;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder
    public BacktestRun(User user, String name, String configJson) {
        this.user = user;
        this.name = name;
        this.configJson = configJson;
        this.status = BacktestStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void complete(String resultJson) {
        this.resultJson = resultJson;
        this.status = BacktestStatus.DONE;
        this.completedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = BacktestStatus.FAILED;
        this.completedAt = Instant.now();
    }
}
