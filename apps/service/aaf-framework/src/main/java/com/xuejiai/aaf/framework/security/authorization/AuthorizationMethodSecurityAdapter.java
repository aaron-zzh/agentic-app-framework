package com.xuejiai.aaf.framework.security.authorization;

import java.io.Serializable;
import java.time.Duration;
import java.util.Map;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Spring Method Security 统一适配入口，不包含独立决策逻辑。 */
@Component
@RequiredArgsConstructor
public final class AuthorizationMethodSecurityAdapter implements PermissionEvaluator {

    private final AuthorizationService authorizationService;

    @Override
    public boolean hasPermission(
            Authentication authentication, Object targetDomainObject, Object permission) {
        if (permission == null) {
            return false;
        }
        var request =
                new AuthorizationRequest(
                        AuthorizationSubject.unresolved(),
                        new AuthorizationTarget(
                                null,
                                null,
                                targetDomainObject == null
                                        ? null
                                        : targetDomainObject.toString()),
                        AuthorizationPlan.functionPermission(permission.toString()),
                        Map.of(),
                        Duration.ofMinutes(10));
        return authorizationService.authorize(request).allowed();
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetType,
            Object permission) {
        if (targetId == null || targetType == null || permission == null) {
            return false;
        }
        var relation =
                new AuthorizationPlan.RelationRequirement(
                        targetType, targetId.toString(), permission.toString());
        var plan =
                new AuthorizationPlan(
                        AuthorizationPlan.FunctionRequirement.authenticated(),
                        AuthorizationPlan.RelationPlan.all(relation),
                        null,
                        null);
        var request =
                new AuthorizationRequest(
                        AuthorizationSubject.unresolved(),
                        new AuthorizationTarget(
                                targetType, permission.toString(), targetId.toString()),
                        plan,
                        Map.of(),
                        Duration.ofMinutes(10));
        return authorizationService.authorize(request).allowed();
    }
}
