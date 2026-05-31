package com.xuejiai.aaf.framework.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.OperatorType;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantContextHolder;

/** 基于 SecurityContext 的 OperatorContext 实现。当前仅支持 Human 场景，AI 场景待 Agent 认证体系落地后扩展。 */
@Component
public class SecurityOperatorContext implements OperatorContext {

    @Override
    public Optional<Long> currentOperatorId() {
        var assistantContext = AssistantContextHolder.get();
        if (assistantContext != null) {
            return Optional.ofNullable(assistantContext.assistantDefinitionId());
        }
        return extractUserId();
    }

    @Override
    public OperatorType currentOperatorType() {
        if (AssistantContextHolder.get() != null) {
            return OperatorType.AI;
        }
        return OperatorType.HUMAN;
    }

    @Override
    public Optional<Long> currentOwnerId() {
        var permissionExecutionContext = PermissionExecutionContextHolder.get();
        if (permissionExecutionContext != null) {
            return Optional.ofNullable(permissionExecutionContext.ownerId());
        }
        var assistantContext = AssistantContextHolder.get();
        if (assistantContext != null) {
            return Optional.ofNullable(assistantContext.delegatorId());
        }
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
