package com.xuejiai.aaf.framework.intelligent.core.assistant;

/** Assistant 执行接口——AAF 对 Assistant 会话能力的统一抽象。 */
public interface AssistantExecutor {

    /**
     * 处理用户消息。
     *
     * @param sessionId 会话 ID
     * @param assistantId 助理 ID
     * @param userId 用户 ID
     * @param userMessage 用户输入
     * @return 助理响应
     */
    AssistantResponse chat(String sessionId, String assistantId, Long userId, String userMessage);

    /** 助理响应 */
    record AssistantResponse(boolean success, String content, String sessionId, String error) {
        public static AssistantResponse success(String content, String sessionId) {
            return new AssistantResponse(true, content, sessionId, null);
        }

        public static AssistantResponse error(String sessionId, String error) {
            return new AssistantResponse(false, null, sessionId, error);
        }
    }
}
