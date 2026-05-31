package com.xuejiai.aaf.framework.security.access;

import org.springframework.data.jpa.domain.Specification;

/** 非注解场景的统一权限决策外观。 */
public interface AccessDecisionService {

    boolean hasPermission(String permissionCode);

    boolean hasPermission(String objectType, String objectId, String relationPermission);

    <T> Specification<T> recordRuleSpec(String entitySlug);

    PolicyResult evaluatePolicy(PolicyInput input);
}
