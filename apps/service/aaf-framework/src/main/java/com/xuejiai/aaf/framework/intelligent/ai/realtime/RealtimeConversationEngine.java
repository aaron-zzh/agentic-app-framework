package com.xuejiai.aaf.framework.intelligent.ai.realtime;

import java.util.function.Consumer;

/** 实时多模态对话引擎。 */
public interface RealtimeConversationEngine {

    /**
     * 打开实时对话。
     *
     * @param config 会话配置
     * @param eventConsumer 服务端事件消费者
     * @return 实时对话句柄
     */
    RealtimeConversation openConversation(
            RealtimeConversationConfig config, Consumer<RealtimeConversationEvent> eventConsumer);
}
