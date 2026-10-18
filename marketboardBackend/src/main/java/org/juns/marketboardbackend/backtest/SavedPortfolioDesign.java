package org.juns.marketboardbackend.backtest;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name="saved_portfolio_designs")
public class SavedPortfolioDesign {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false, updatable=false) private Long userId;
    @Column(nullable=false, updatable=false, length=100) private String name;
    @Column(name="selected_id", nullable=false, updatable=false, length=40) private String selectedId;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="payload_json", nullable=false, updatable=false, columnDefinition="MEDIUMTEXT") private String payloadJson;
    protected SavedPortfolioDesign() {}
    public SavedPortfolioDesign(Long userId, String name, String selectedId, String payloadJson) {
        this.userId=userId; this.name=name; this.selectedId=selectedId; this.payloadJson=payloadJson;
        this.createdAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSelectedId() { return selectedId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPayloadJson() { return payloadJson; }
}
