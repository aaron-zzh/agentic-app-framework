package com.xuejiai.aaf.module.ai.aigc.realtime;

/** 由已认证调用者、合法 hello 和 transport 创建冻结实时会话。 */
public interface RealtimeConversationSessionFactory {

    RealtimeConversationSession open(
            RealtimeCaller caller, RealtimeHello hello, RealtimeSessionTransport transport);
}
