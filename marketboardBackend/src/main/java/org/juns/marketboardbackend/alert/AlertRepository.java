package org.juns.marketboardbackend.alert;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<Alert> findByIdAndUser_Id(Long id, Long userId);

    List<Alert> findByTriggeredAtIsNull();

    void deleteBySymbol_Id(Long symbolId);

    void deleteByUser_Id(Long userId);
}
