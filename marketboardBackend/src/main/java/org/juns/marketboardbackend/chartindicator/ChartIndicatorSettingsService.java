package org.juns.marketboardbackend.chartindicator;

import java.util.List;
import org.juns.marketboardbackend.chartindicator.dto.ChartIndicatorSettingsRequest;
import org.juns.marketboardbackend.chartindicator.dto.ChartIndicatorSettingsResponse;
import org.juns.marketboardbackend.chartindicator.dto.SmaOverlayConfig;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-user SMA-overlay period preferences for the price chart. Deliberately not a
 * backend-computed/cached indicator: the actual SMA values are derived client-side from whatever
 * candles are already on screen (see CandleChart's computeSma), so this only ever stores which
 * periods the user wants -- an unbounded, per-user choice that couldn't be cached server-side anyway.
 */
@Service
public class ChartIndicatorSettingsService {

    private static final List<SmaOverlayConfig> DEFAULT_OVERLAYS = List.of(new SmaOverlayConfig(20), new SmaOverlayConfig(50));

    private final ChartIndicatorSettingsRepository chartIndicatorSettingsRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ChartIndicatorSettingsService(
            ChartIndicatorSettingsRepository chartIndicatorSettingsRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.chartIndicatorSettingsRepository = chartIndicatorSettingsRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ChartIndicatorSettingsResponse get(Long userId) {
        return chartIndicatorSettingsRepository
                .findByUser_Id(userId)
                .map(settings -> new ChartIndicatorSettingsResponse(readOverlays(settings.getSettingsJson())))
                .orElseGet(() -> new ChartIndicatorSettingsResponse(DEFAULT_OVERLAYS));
    }

    @Transactional
    public ChartIndicatorSettingsResponse save(Long userId, ChartIndicatorSettingsRequest request) {
        String settingsJson = objectMapper.writeValueAsString(request.smaOverlays());
        ChartIndicatorSettings settings = chartIndicatorSettingsRepository.findByUser_Id(userId).orElse(null);
        if (settings == null) {
            User user = userRepository.getReferenceById(userId);
            settings = ChartIndicatorSettings.builder().user(user).settingsJson(settingsJson).build();
            chartIndicatorSettingsRepository.save(settings);
        } else {
            settings.update(settingsJson);
        }
        return new ChartIndicatorSettingsResponse(request.smaOverlays());
    }

    private List<SmaOverlayConfig> readOverlays(String settingsJson) {
        SmaOverlayConfig[] overlays = objectMapper.readValue(settingsJson, SmaOverlayConfig[].class);
        return List.of(overlays);
    }
}
