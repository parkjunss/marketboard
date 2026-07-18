package org.juns.marketboardbackend.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortfolioCreateRequest(@NotBlank @Size(max = 100) String name) {
}
