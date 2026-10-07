package org.juns.marketboardbackend.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {
    List<ReviewDecision> findByReviewIdAndUserIdOrderByIdDesc(Long reviewId, Long userId);
}
