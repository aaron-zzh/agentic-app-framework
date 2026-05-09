package com.xuejiai.aaf.framework.security;

import java.util.Optional;

/**
 * 当前操作者上下文。
 *
 * <p>统一抽象人类用户和 AI Agent 的身份信息，供业务层获取当前登录者。 AAF-022 实现完整的 Actor 体系后替换此占位。
 */
public interface ActorContext {

    /** 获取当前操作者 ID，未登录返回 empty */
    Optional<Long> currentUserId();

    /** 是否已认证 */
    boolean isAuthenticated();
}
