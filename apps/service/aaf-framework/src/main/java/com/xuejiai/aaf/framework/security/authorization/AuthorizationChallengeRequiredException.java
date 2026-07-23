package com.xuejiai.aaf.framework.security.authorization;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 授权需要人工确认；调用方必须保留 challengeId，不能降级为普通无权限。 */
@Getter
public final class AuthorizationChallengeRequiredException extends RuntimeException {

    private final UUID challengeId;

    public AuthorizationChallengeRequiredException(UUID challengeId) {
        super("授权确认待处理");
        this.challengeId = Objects.requireNonNull(challengeId, "challengeId");
    }
}
