package com.xuejiai.aaf.framework.security.authorization;

/** 语义授权目标，不绑定数据库实体。 */
public record AuthorizationTarget(String resource, String action, String objectId) {

    public static AuthorizationTarget none() {
        return new AuthorizationTarget(null, null, null);
    }

    public boolean hasPolicyTarget() {
        return resource != null && !resource.isBlank() && action != null && !action.isBlank();
    }
}
