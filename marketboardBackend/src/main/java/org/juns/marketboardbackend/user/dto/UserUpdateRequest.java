package org.juns.marketboardbackend.user.dto;

import jakarta.validation.constraints.NotNull;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.UserStatus;

public record UserUpdateRequest(@NotNull Role role, @NotNull UserStatus status) {
}
