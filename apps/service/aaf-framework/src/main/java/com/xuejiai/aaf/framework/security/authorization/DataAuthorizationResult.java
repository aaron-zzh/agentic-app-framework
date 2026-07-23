package com.xuejiai.aaf.framework.security.authorization;

import java.util.Objects;

/** L3 Provider 的类型安全结果；明确允许时必须携带可执行约束。 */
public record DataAuthorizationResult(
        AuthorizationEffect effect, AuthorizationConstraint constraint, String reason) {

    public DataAuthorizationResult {
        Objects.requireNonNull(effect, "effect");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason 不能为空");
        }
        if (effect == AuthorizationEffect.ALLOW && constraint == null) {
            throw new IllegalArgumentException("L3 ALLOW 必须携带授权约束");
        }
        if (effect != AuthorizationEffect.ALLOW && constraint != null) {
            throw new IllegalArgumentException("非 ALLOW 的 L3 结果不得携带授权约束");
        }
    }

    public static DataAuthorizationResult allow(
            AuthorizationConstraint constraint, String reason) {
        return new DataAuthorizationResult(
                AuthorizationEffect.ALLOW, Objects.requireNonNull(constraint, "constraint"), reason);
    }

    public static DataAuthorizationResult indeterminate(String reason) {
        return new DataAuthorizationResult(AuthorizationEffect.INDETERMINATE, null, reason);
    }
}
