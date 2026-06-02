package com.xuejiai.aaf.framework.engine.entitlement;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 权益配额 AOP 切面——与四层权限平行。
 *
 * <p>执行顺序：RBAC(@PreAuthorize) → @Entitlement(本切面) → 方法执行 → 成功后扣减。
 *
 * <p>方法抛异常时不扣减（保证幂等）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class EntitlementAspect {

    private final EntitlementChecker entitlementChecker;
    private final OperatorContext operatorContext;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(entitlement)")
    public Object around(ProceedingJoinPoint joinPoint, Entitlement entitlement) throws Throwable {
        var userId = getCurrentUserId();
        var cost = resolveCost(entitlement.cost(), joinPoint);

        // 1. 执行前检查额度是否足够（不足直接抛异常，方法不执行）
        entitlementChecker.check(userId, entitlement.code(), cost);

        // 2. 额度充足，执行业务方法
        var result = joinPoint.proceed();

        // 3. 方法成功后真扣减（方法抛异常时不扣减）
        entitlementChecker.consume(userId, entitlement.code(), cost);

        return result;
    }

    /** 解析 SpEL 表达式获取 cost 值 */
    private long resolveCost(String costExpression, ProceedingJoinPoint joinPoint) {
        // 纯数字直接返回
        try {
            return Long.parseLong(costExpression);
        } catch (NumberFormatException ignored) {
            // SpEL 表达式
        }

        var signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        var context =
                new MethodBasedEvaluationContext(
                        null, method, joinPoint.getArgs(), paramDiscoverer);
        var value = parser.parseExpression(costExpression).getValue(context);
        if (value instanceof Number num) {
            return num.longValue();
        }
        throw new IllegalArgumentException("@Entitlement cost 表达式必须解析为数值: " + costExpression);
    }

    /** 优先从 OperatorContext 获取 owner；兼容普通 Spring Security principal。 */
    private Long getCurrentUserId() {
        var ownerId = operatorContext.currentOwnerId().orElse(null);
        if (ownerId != null) {
            return ownerId;
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        // 兼容 principal 为字符串数字的场景
        if (auth != null && auth.getPrincipal() != null) {
            try {
                return Long.parseLong(auth.getPrincipal().toString());
            } catch (NumberFormatException e) {
                // 忽略
            }
        }
        throw new IllegalStateException("无法获取当前用户 ID，@Entitlement 需要认证上下文");
    }
}
