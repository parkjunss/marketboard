package org.juns.marketboardbackend.auth;

import jakarta.validation.constraints.NotBlank;

public record OAuthExchangeRequest(@NotBlank String code) {}
