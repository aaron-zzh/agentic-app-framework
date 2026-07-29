package com.xuejiai.aaf.framework.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.OperatorType;
import com.xuejiai.aaf.framework.task.TaskExecutionContextHolder;

/** 基于 SecurityContext 的 OperatorContext 实现。当前仅支持 Human 场景，AI 场景待 Agent 认证体系落地后扩展。 */
@Component
public class SecurityOperatorContext implements OperatorContext {

    @Override
    public Optional<Long> currentOperatorId() {
        return extractUserId();
    }

    @Override
    public OperatorType currentOperatorType() {
        return OperatorType.HUMAN;
    }

    @Override
    public Optional<Long> currentOwnerId() {
        // 定时任务上下文优先级最高（任务执行线程无 SecurityContext）
        var taskOwnerId = TaskExecutionContextHolder.get();
        if (taskOwnerId != null) {
            return Optional.of(taskOwnerId);
        }
        var permissionExecutionContext = PermissionExecutionContextHolder.get();
        if (permissionExecutionContext != null) {
            return Optional.ofNullable(permissionExecutionContext.ownerId());
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
            // 兜底：兼容 UserDetails 等其他 principal 类型（如测试环境
            // SecurityMockMvcRequestPostProcessors.user(String) 构造的 principal）。
            // Authentication#getName() 是标准接口方法，对 UserDetails 返回 getUsername()。
            return Optional.of(Long.valueOf(auth.getName()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
