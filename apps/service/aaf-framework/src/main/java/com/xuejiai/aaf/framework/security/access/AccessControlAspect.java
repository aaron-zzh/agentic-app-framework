package com.xuejiai.aaf.framework.security.access;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.ActorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Layer 1 权限切面——处理 @AccessControl 注解。
 *
 * <p>校验通过后设置标记，Layer 2/3 可通过 {@link AccessContext} 判断是否已处理。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AccessControlAspect {

    private final ActorContext actorContext;

    @Around("@annotation(accessControl)")
    public Object enforce(ProceedingJoinPoint joinPoint, AccessControl accessControl)
            throws Throwable {
        // 认证检查
        var userId = actorContext.currentUserId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));

        // 角色检查
        if (accessControl.roles().length > 0) {
            // TODO: 从 SecurityContext 获取用户角色并匹配
            log.debug("Layer1 角色检查: userId={}, required={}", userId, accessControl.roles());
        }

        // 功能开关检查
        if (!accessControl.feature().isEmpty()) {
            // TODO: 查询功能开关是否启用
            log.debug("Layer1 功能开关: feature={}", accessControl.feature());
        }

        // 标记 Layer 1 已处理
        AccessContext.markProcessed(AccessLayer.ANNOTATION);

        return joinPoint.proceed();
    }
}
