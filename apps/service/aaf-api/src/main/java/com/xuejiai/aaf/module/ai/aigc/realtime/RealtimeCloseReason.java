package com.xuejiai.aaf.module.ai.aigc.realtime;

/** v1 会话关闭原因。 */
public enum RealtimeCloseReason {
    NORMAL,
    PROTOCOL_ERROR,
    UNAUTHORIZED,
    CONTEXT_REJECTED,
    ENGINE_ERROR
}
