package com.xuejiai.aaf.framework.security.authorization;

import java.util.Collection;

/** 统一授权效果，数值越大安全优先级越高。 */
public enum AuthorizationEffect {
    NOT_APPLICABLE(0),
    ALLOW(1),
    CHALLENGE(2),
    INDETERMINATE(3),
    DENY(4);

    private final int priority;

    AuthorizationEffect(int priority) {
        this.priority = priority;
    }

    public static AuthorizationEffect strongest(Collection<AuthorizationEffect> effects) {
        return effects.stream()
                .filter(effect -> effect != NOT_APPLICABLE)
                .max((left, right) -> Integer.compare(left.priority, right.priority))
                .orElse(NOT_APPLICABLE);
    }
}
