package org.juns.marketboardbackend.dashboard;

import java.util.List;
import org.juns.marketboardbackend.dashboard.dto.DashboardConfigRequest;
import org.juns.marketboardbackend.dashboard.dto.DashboardConfigResponse;
import org.juns.marketboardbackend.dashboard.dto.PanelConfigDto;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DashboardConfigService {

    private static final String DEFAULT_LAYOUT_KEY = "TWO_COLUMN";

    private final DashboardConfigRepository dashboardConfigRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public DashboardConfigService(
            DashboardConfigRepository dashboardConfigRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.dashboardConfigRepository = dashboardConfigRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DashboardConfigResponse get(Long userId) {
        return dashboardConfigRepository
                .findByUser_Id(userId)
                .map(config -> new DashboardConfigResponse(config.getLayoutKey(), readPanels(config.getPanelsJson())))
                .orElseGet(() -> new DashboardConfigResponse(DEFAULT_LAYOUT_KEY, List.of()));
    }

    @Transactional
    public DashboardConfigResponse save(Long userId, DashboardConfigRequest request) {
        String panelsJson = objectMapper.writeValueAsString(request.panels());
        DashboardConfig config = dashboardConfigRepository.findByUser_Id(userId).orElse(null);
        if (config == null) {
            User user = userRepository.getReferenceById(userId);
            config = DashboardConfig.builder().user(user).layoutKey(request.layoutKey()).panelsJson(panelsJson).build();
            dashboardConfigRepository.save(config);
        } else {
            config.update(request.layoutKey(), panelsJson);
        }
        return new DashboardConfigResponse(config.getLayoutKey(), request.panels());
    }

    private List<PanelConfigDto> readPanels(String panelsJson) {
        PanelConfigDto[] panels = objectMapper.readValue(panelsJson, PanelConfigDto[].class);
        return List.of(panels);
    }
}
