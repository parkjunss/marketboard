package org.juns.marketboardbackend.portfolio;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUser_IdOrderByCreatedAtAsc(Long userId);

    Optional<Portfolio> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Portfolio p where p.id = :id and p.user.id = :userId")
    Optional<Portfolio> findOwnedByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
