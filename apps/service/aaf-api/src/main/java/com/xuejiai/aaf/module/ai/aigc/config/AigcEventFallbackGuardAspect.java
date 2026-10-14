package com.xuejiai.aaf.module.ai.aigc.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

/**
 * 无事务发布事件的告警守卫。
 *
 * <p>AIGC 的 {@code AFTER_COMMIT} 监听器保留 {@code fallbackExecution = true}，避免发布点没有事务时事件被静默丢弃导致异步链路卡住。
 * 但这意味着发布点一旦缺少事务，监听器会在提交前同步执行，破坏"先落库、后派发"的顺序。本切面在监听器入口检测当前是否存在事务： 正常的提交后回调仍处于原事务的完成阶段，{@code
 * isActualTransactionActive()} 为 true；没有事务时为 false，此时记录 ERROR 以暴露问题， 不改变执行行为。
 *
 * <p>切面优先级高于事务切面，保证检测发生在监听器自身的 {@code REQUIRES_NEW} 事务开启之前。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AigcEventFallbackGuardAspect {

    @Around(
            value = "@annotation(listener) && within(com.xuejiai.aaf.module.ai.aigc..*)",
            argNames = "pjp,listener")
    public Object around(ProceedingJoinPoint pjp, TransactionalEventListener listener)
            throws Throwable {
        if (listener.fallbackExecution()
                && !TransactionSynchronizationManager.isActualTransactionActive()) {
            var method = ((MethodSignature) pjp.getSignature()).getMethod();
            log.error(
                    "[事务事件守卫] 事件在无事务环境下被发布，监听器经 fallbackExecution 立即执行，"
                            + "可能早于数据提交: {}#{}，请检查发布点是否位于事务内",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName());
        }
        return pjp.proceed();
    }
}
