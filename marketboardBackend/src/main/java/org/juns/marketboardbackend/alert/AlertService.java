package org.juns.marketboardbackend.alert;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.juns.marketboardbackend.alert.dto.AlertRequest;
import org.juns.marketboardbackend.alert.dto.AlertResponse;
import org.juns.marketboardbackend.common.exception.ResourceNotFoundException;
import org.juns.marketboardbackend.symbol.Symbol;
import org.juns.marketboardbackend.symbol.SymbolRepository;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final SymbolRepository symbolRepository;
    private final UserRepository userRepository;
    private final AlertRedisMirror alertRedisMirror;
    private final MeterRegistry meterRegistry;

    public AlertService(
            AlertRepository alertRepository,
            SymbolRepository symbolRepository,
            UserRepository userRepository,
            AlertRedisMirror alertRedisMirror,
            MeterRegistry meterRegistry) {
        this.alertRepository = alertRepository;
        this.symbolRepository = symbolRepository;
        this.userRepository = userRepository;
        this.alertRedisMirror = alertRedisMirror;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAll(Long userId) {
        return alertRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(AlertResponse::from)
                .toList();
    }

    @Transactional
    public AlertResponse create(Long userId, AlertRequest request) {
        Symbol symbol = symbolRepository.findByTickerIgnoreCase(request.ticker())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown symbol " + request.ticker().toUpperCase()));
        User user = userRepository.getReferenceById(userId);
        Alert saved = alertRepository.save(Alert.builder()
                .user(user)
                .symbol(symbol)
                .condition(request.condition())
                .targetPrice(request.targetPrice())
                .build());
        alertRedisMirror.mirror(saved);
        meterRegistry.counter("marketboard.alerts.created").increment();
        return AlertResponse.from(saved);
    }

    @Transactional
    public void delete(Long userId, Long alertId) {
        Alert alert = alertRepository.findByIdAndUser_Id(alertId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));
        alertRedisMirror.remove(alert);
        alertRepository.delete(alert);
    }
}
