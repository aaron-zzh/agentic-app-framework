package com.xuejiai.aaf.framework.security.authorization;

/** 授权主体；operatorId 用于审计，subjectId 用于权限判定。 */
public record AuthorizationSubject(
        Long operatorId, Long subjectId, Long tenantId, Long workspaceId) {

    public static AuthorizationSubject unresolved() {
        return new AuthorizationSubject(null, null, null, null);
    }

    public AuthorizationSubject resolve(Long resolvedOperatorId, Long resolvedSubjectId) {
        return new AuthorizationSubject(
                operatorId == null ? resolvedOperatorId : operatorId,
                subjectId == null ? resolvedSubjectId : subjectId,
                tenantId,
                workspaceId);
    }
}
