package org.juns.marketboardbackend.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop100ByUser_IdOrderByCreatedAtDesc(Long userId);
    List<Notification> findByUser_IdAndReadAtIsNull(Long userId);
    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);
    long countByUser_IdAndReadAtIsNull(Long userId);
    boolean existsByUser_IdAndDedupKey(Long userId, String dedupKey);
}
