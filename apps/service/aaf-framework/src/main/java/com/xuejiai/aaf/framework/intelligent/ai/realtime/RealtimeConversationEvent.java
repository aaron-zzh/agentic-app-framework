package com.xuejiai.aaf.framework.intelligent.ai.realtime;

import java.util.Map;

/** 实时多模态对话事件。 */
public record RealtimeConversationEvent(
        /** 事件类型 */
        String type,
        /** 文本内容 */
        String text,
        /** 音频数据片段 */
        byte[] audio,
        /** 额外属性 */
        Map<String, Object> extra) {}
