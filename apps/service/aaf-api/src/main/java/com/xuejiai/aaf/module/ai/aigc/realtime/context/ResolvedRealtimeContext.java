package com.xuejiai.aaf.module.ai.aigc.realtime.context;

import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi.ConversationContext;
import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi.ConversationIdentity;
import com.xuejiai.aaf.module.device.api.RealtimeDeviceContext;

/** 已冻结的实时会话上下文。 */
public record ResolvedRealtimeContext(
        ConversationIdentity identity,
        ConversationContext assistantContext,
        String endpointing,
        RealtimeDeviceContext deviceContext) {}
