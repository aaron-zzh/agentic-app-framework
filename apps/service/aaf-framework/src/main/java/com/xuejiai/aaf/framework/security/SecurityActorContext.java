package com.xuejiai.aaf.framework.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** 基于 SecurityContext 的 ActorContext 实现。 */
@Component
public class SecurityActorContext implements ActorContext {

    @Override
    public Optional<Long> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                return Optional.of(Long.valueOf(jwt.getSubject()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isAuthenticated() {
        return currentUserId().isPresent();
    }
}
