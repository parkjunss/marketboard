package org.juns.marketboardbackend.user.dto;

import org.juns.marketboardbackend.user.User;

public record ProfileResponse(Long id, String email, String username, boolean dailyReportEnabled,
                              boolean priceAlertEnabled, boolean importantInfoEnabled) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getUsername(), user.isDailyReportEnabled(),
                user.isPriceAlertEnabled(), user.isImportantInfoEnabled());
    }
}
