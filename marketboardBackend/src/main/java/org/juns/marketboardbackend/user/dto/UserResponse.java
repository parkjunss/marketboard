package org.juns.marketboardbackend.user.dto;

import java.time.Instant;
import org.juns.marketboardbackend.user.Role;
import org.juns.marketboardbackend.user.User;
import org.juns.marketboardbackend.user.UserStatus;

public record UserResponse(Long id, String email, String username, Role role, UserStatus status, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
