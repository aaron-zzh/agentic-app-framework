package com.xuejiai.aaf.framework.intelligent.core.assistant;

import java.util.List;

/**
 * 会话上下文解析器——按 AG-UI threadId 解析会话归属与历史。
 *
 * <p>framework 层定义契约，api 层（ai.chat 模块）实现，桥接 ChatSession/ChatMessage。 供 {@link AafAgentResolver} 在
 * /agui/runs 链路冷启动时设置上下文、播种历史。
 */
public interface ChatSessionResolver {

    /** 会话上下文 */
    record SessionContext(Long userId, String assistantId, Long knowledgeBaseId, Long sessionId) {}

    /** 历史消息 */
    record HistoryMessage(String role, String content) {}

    /** 按 threadId 解析会话上下文，不存在时返回 null。 */
    SessionContext resolveByThreadId(String threadId);

    /** 按 sessionId 查询历史消息（按时间升序）。 */
    List<HistoryMessage> loadHistory(Long sessionId);
}
