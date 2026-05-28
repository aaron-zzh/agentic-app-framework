package com.xuejiai.aaf.framework.security;

import java.util.Optional;

import com.xuejiai.aaf.common.enums.OperatorType;

/**
 * 操作者上下文——统一抽象人类用户和 AI 助理的身份信息。
 *
 * <p>业务代码通过本接口获取当前操作者，不关心底层认证方式。
 */
public interface OperatorContext {

    /** 当前操作者 ID（user.id 或 assistant.id） */
    Optional<Long> currentOperatorId();

    /** 操作者类型 */
    OperatorType currentOperatorType();

    /** 数据归属者 ID（始终为 user.id；AI 操作时为委托者） */
    Optional<Long> currentOwnerId();

    /** 是否已认证 */
    boolean isAuthenticated();

    /** 兼容旧调用：等同于 currentOwnerId */
    default Optional<Long> currentUserId() {
        return currentOwnerId();
    }
}
