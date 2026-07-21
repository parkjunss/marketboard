package org.juns.marketboardbackend.chartindicator;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** A user's saved SMA-overlay periods for the price chart (see ChartIndicatorSettingsService). */
@Entity
@Table(name = "chart_indicator_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChartIndicatorSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "settings_json", nullable = false, columnDefinition = "TEXT")
    private String settingsJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public ChartIndicatorSettings(User user, String settingsJson) {
        this.user = user;
        this.settingsJson = settingsJson;
        this.updatedAt = Instant.now();
    }

    public void update(String settingsJson) {
        this.settingsJson = settingsJson;
        this.updatedAt = Instant.now();
    }
}
