package org.juns.marketboardbackend.review;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestmentReviewRepository extends JpaRepository<InvestmentReview, Long> {
    List<InvestmentReview> findTop50ByUserIdOrderByIdDesc(Long userId);
    Optional<InvestmentReview> findByIdAndUserId(Long id, Long userId);
}
