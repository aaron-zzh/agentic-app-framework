package com.xuejiai.aaf.framework.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.OperatorType;

/** 基于 SecurityContext 的 OperatorContext 实现。当前仅支持 Human 场景，AI 场景待 Agent 认证体系落地后扩展。 */
@Component
public class SecurityOperatorContext implements OperatorContext {

    @Override
    public Optional<Long> currentOperatorId() {
        return extractUserId();
    }

    @Override
    public OperatorType currentOperatorType() {
        // 当前所有通过 SecurityContext 认证的都是 Human；AI 场景后续通过 AgentPrincipal 区分
        return OperatorType.HUMAN;
    }

    @Override
    public Optional<Long> currentOwnerId() {
        // Human 场景：owner = operator 自身
        return extractUserId();
    }

    @Override
    public boolean isAuthenticated() {
        return extractUserId().isPresent();
    }

    private Optional<Long> extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof Jwt jwt) {
                return Optional.of(Long.valueOf(jwt.getSubject()));
            }
            if (principal instanceof String str) {
                return Optional.of(Long.valueOf(str));
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
