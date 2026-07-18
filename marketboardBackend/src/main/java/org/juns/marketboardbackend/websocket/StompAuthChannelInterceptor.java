package org.juns.marketboardbackend.websocket;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.juns.marketboardbackend.common.exception.InvalidTokenException;
import org.juns.marketboardbackend.security.AuthenticatedUser;
import org.juns.marketboardbackend.security.JwtTokenProvider;
import org.juns.marketboardbackend.security.TokenType;
import org.juns.marketboardbackend.user.Role;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // MessageHeaderAccessor.getAccessor (not StompHeaderAccessor.wrap) retrieves the message's
        // own live, mutable accessor — StompHeaderAccessor.wrap() returns a detached copy, so
        // accessor.setUser() on it would silently never reach the message actually sent downstream.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            throw new InvalidTokenException();
        }
        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            if (!TokenType.ACCESS.name().equals(claims.get("type", String.class))) {
                throw new InvalidTokenException();
            }
            Long userId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            Role role = Role.valueOf(claims.get("role", String.class));
            AuthenticatedUser principal = new AuthenticatedUser(userId, email, role);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
