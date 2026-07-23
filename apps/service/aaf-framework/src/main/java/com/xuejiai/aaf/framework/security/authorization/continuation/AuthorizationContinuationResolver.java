package com.xuejiai.aaf.framework.security.authorization.continuation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 从当前 HTTP 请求解析一次性授权 continuation。 */
public final class AuthorizationContinuationResolver {

    public static final String HEADER_NAME = "X-Authorization-Challenge-Id";

    private static final String CONSUMED_ATTRIBUTE =
            AuthorizationContinuationResolver.class.getName() + ".consumed";

    private AuthorizationContinuationResolver() {}

    public static Optional<UUID> resolve() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        var request = servletAttributes.getRequest();
        var value = request.getHeader(HEADER_NAME);
        if (value == null) {
            return Optional.empty();
        }
        try {
            var challengeId = UUID.fromString(value);
            if (!challengeId.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("授权 continuation 标识格式非法");
            }
            if (challengeId.equals(request.getAttribute(CONSUMED_ATTRIBUTE))) {
                return Optional.empty();
            }
            return Optional.of(challengeId);
        } catch (IllegalArgumentException cause) {
            throw new IllegalArgumentException("授权 continuation 标识格式非法", cause);
        }
    }

    /** 标记当前 HTTP 请求已成功消费 continuation，后续授权检查不得重复使用。 */
    public static void markConsumed(UUID challengeId) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            servletAttributes.getRequest().setAttribute(CONSUMED_ATTRIBUTE, challengeId);
        }
    }
}
