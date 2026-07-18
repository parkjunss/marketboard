package org.juns.marketboardbackend.dashboard;

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

@Entity
@Table(name = "dashboard_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "layout_key", nullable = false, length = 30)
    private String layoutKey;

    @Column(name = "panels_json", nullable = false, columnDefinition = "TEXT")
    private String panelsJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public DashboardConfig(User user, String layoutKey, String panelsJson) {
        this.user = user;
        this.layoutKey = layoutKey;
        this.panelsJson = panelsJson;
        this.updatedAt = Instant.now();
    }

    public void update(String layoutKey, String panelsJson) {
        this.layoutKey = layoutKey;
        this.panelsJson = panelsJson;
        this.updatedAt = Instant.now();
    }
}
