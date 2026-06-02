package com.xuejiai.aaf.framework.security.access;

import java.io.Serializable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/** Spring Security hasPermission 统一出口：L1 功能权限 + L2 关系权限。 */
@Component
@RequiredArgsConstructor
public class AafPermissionEvaluator implements PermissionEvaluator {

    private final OperatorContext operatorContext;
    private final ObjectProvider<FunctionPermissionChecker> functionPermissionChecker;
    private final ObjectProvider<RelationPermissionChecker> relationPermissionChecker;

    /** L1 功能权限：@PreAuthorize("hasPermission(null, 'system:user:create')")。 */
    @Override
    public boolean hasPermission(
            Authentication authentication, Object targetDomainObject, Object permission) {
        if (permission == null) {
            return false;
        }
        if (hasSuperAdmin(authentication)) {
            return true;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        if (userId == null) {
            return false;
        }
        var checker = functionPermissionChecker.getIfAvailable();
        return checker != null && checker.hasPermission(userId, permission.toString());
    }

    /** L2 关系权限：@PreAuthorize("hasPermission(#id, 'document', 'can_read')")。 */
    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetType,
            Object permission) {
        if (targetId == null || targetType == null || permission == null) {
            return false;
        }
        if (hasSuperAdmin(authentication)) {
            return true;
        }
        var userId = operatorContext.currentOwnerId().orElse(null);
        if (userId == null) {
            return false;
        }
        var checker = relationPermissionChecker.getIfAvailable();
        return checker != null
                && checker.hasPermission(
                        userId, targetType, targetId.toString(), permission.toString());
    }

    private boolean hasSuperAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority));
    }
}
