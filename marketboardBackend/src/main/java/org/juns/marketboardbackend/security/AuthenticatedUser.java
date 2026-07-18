package org.juns.marketboardbackend.security;

import java.security.Principal;
import org.juns.marketboardbackend.user.Role;

/**
 * Implements Principal so Spring's STOMP user-destination resolver (convertAndSendToUser)
 * routes on the numeric user id (getName()) instead of falling back to Object#toString().
 */
public record AuthenticatedUser(Long id, String email, Role role) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(id);
    }
}
