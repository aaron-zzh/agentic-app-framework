package com.xuejiai.aaf.framework.security.access;

import org.springframework.data.jpa.domain.Specification;

/** 非注解场景的统一权限决策外观。 */
public interface AccessDecisionService {

    boolean hasPermission(String permissionCode);

    /**
     * 权限码是否已在系统中注册。未注册时应降级为仅登录校验，避免未补充权限码数据的业务实体被误锁。
     *
     * @param permissionCode 三段式权限码
     * @return true=已注册
     */
    boolean isPermissionCodeRegistered(String permissionCode);

    boolean hasPermission(String objectType, String objectId, String relationPermission);

    <T> Specification<T> recordRuleSpec(String entitySlug);

    PolicyResult evaluatePolicy(PolicyInput input);
}
