package com.xuejiai.aaf.framework.org;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * {@link OrgIgnore} 注解对应的切面——方法执行期间将 {@link OrgContext} 标记为忽略组织过滤，
 * 执行完毕后还原，避免污染同线程后续复用（如线程池复用线程执行下一个任务）。
 */
@Aspect
@Component
public class OrgIgnoreAspect {

    @Around(
            "@annotation(com.xuejiai.aaf.framework.org.OrgIgnore) "
                    + "|| @within(com.xuejiai.aaf.framework.org.OrgIgnore)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        var oldIgnore = OrgContext.isIgnore();
        try {
            OrgContext.setIgnore(true);
            return joinPoint.proceed();
        } finally {
            OrgContext.setIgnore(oldIgnore);
        }
    }
}
