package org.juns.marketboardbackend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(min = 2, max = 30) String username,
        boolean dailyReportEnabled,
        boolean priceAlertEnabled,
        boolean importantInfoEnabled) {}
