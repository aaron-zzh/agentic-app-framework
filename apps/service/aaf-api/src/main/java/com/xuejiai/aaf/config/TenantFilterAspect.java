package com.xuejiai.aaf.config;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** AOP 切面：在 Repository 方法执行前自动启用租户过滤器。 */
@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    private final EntityManager entityManager;

    @Before("execution(* com.xuejiai.aaf.module..repository.*.*(..))")
    public void enableTenantFilter() {
        var orgId = TenantContext.getCurrentOrgId();
        if (orgId != null) {
            var session = entityManager.unwrap(org.hibernate.Session.class);
            session.enableFilter("tenantFilter").setParameter("orgId", orgId);
        }
    }
}
