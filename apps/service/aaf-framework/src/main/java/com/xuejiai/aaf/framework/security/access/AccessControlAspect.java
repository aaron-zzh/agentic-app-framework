package com.xuejiai.aaf.framework.security.access;

import java.util.Arrays;
import java.util.Collection;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Layer 1 权限切面——处理 @AccessControl 注解。
 *
 * <p>校验通过后设置标记，Layer 2/3 可通过 {@link AccessContext} 判断是否已处理。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AccessControlAspect {

    private final OperatorContext operatorContext;
    private final FeatureToggleChecker featureToggleChecker;

    @Around("@annotation(accessControl)")
    public Object enforce(ProceedingJoinPoint joinPoint, AccessControl accessControl)
            throws Throwable {
        // 认证检查
        var userId =
                operatorContext
                        .currentUserId()
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));

        // 角色检查
        if (accessControl.roles().length > 0) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            Collection<? extends GrantedAuthority> authorities =
                    authentication != null ? authentication.getAuthorities() : java.util.List.of();
            var userRoles =
                    authorities.stream().map(GrantedAuthority::getAuthority).toList();
            boolean matched =
                    Arrays.stream(accessControl.roles())
                            .anyMatch(
                                    required ->
                                            userRoles.contains(required)
                                                    || userRoles.contains("ROLE_" + required));
            if (!matched) {
                log.debug("Layer1 角色不匹配: userId={}, required={}, actual={}", userId, accessControl.roles(), userRoles);
                throw new BusinessException(GlobalErrorCode.FORBIDDEN);
            }
        }

        // 功能开关检查
        if (!accessControl.feature().isEmpty()) {
            if (!featureToggleChecker.isEnabled(accessControl.feature())) {
                log.debug("Layer1 功能未启用: feature={}", accessControl.feature());
                throw new BusinessException(GlobalErrorCode.FORBIDDEN, "功能未启用: " + accessControl.feature());
            }
        }

        // 标记 Layer 1 已处理
        AccessContext.markProcessed(AccessLayer.ANNOTATION);

        return joinPoint.proceed();
    }
}
