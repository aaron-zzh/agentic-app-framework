package com.xuejiai.aaf.framework.security.access;

/** ABAC 策略评估结果。 */
public record PolicyResult(boolean allowed, String reason) {

    public static PolicyResult allow() {
        return new PolicyResult(true, null);
    }

    public static PolicyResult deny(String reason) {
        return new PolicyResult(false, reason);
    }
}
