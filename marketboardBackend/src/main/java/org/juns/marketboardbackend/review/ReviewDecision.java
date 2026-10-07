package org.juns.marketboardbackend.review;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "review_decisions")
public class ReviewDecision {
    public enum Choice { EXECUTE, DEFER, HOLD }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "review_id", nullable = false, updatable = false) private Long reviewId;
    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 20) private Choice choice;
    @Column(nullable = false, updatable = false, length = 2000) private String reason;
    @Column(name = "follow_up_date", updatable = false) private LocalDate followUpDate;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ReviewDecision() {}
    public ReviewDecision(Long reviewId, Long userId, Choice choice, String reason, LocalDate followUpDate) {
        this.reviewId = reviewId; this.userId = userId; this.choice = choice; this.reason = reason.trim();
        this.followUpDate = followUpDate; this.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public Long getId() { return id; }
    public Long getReviewId() { return reviewId; }
    public Choice getChoice() { return choice; }
    public String getReason() { return reason; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public Instant getCreatedAt() { return createdAt; }
}
