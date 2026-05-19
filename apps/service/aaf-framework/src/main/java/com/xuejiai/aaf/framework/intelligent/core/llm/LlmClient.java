package com.xuejiai.aaf.framework.intelligent.core.llm;

import java.util.List;

import reactor.core.publisher.Flux;

/**
 * LLM 调用接口——统一 Spring AI 和 AgentScope 两种实现。
 * 上层只依赖此接口，底层实现可替换。
 */
public interface LlmClient {

    /**
     * 同步调用 LLM。
     *
     * @param messages 消息列表（role + content）
     * @param scene    场景名（用于模型路由）
     * @param userId   用户 ID（用于 Token 计量）
     * @return LLM 响应文本
     */
    String call(List<LlmMessage> messages, String scene, Long userId);

    /**
     * 流式调用 LLM。
     *
     * @return 流式响应（每个元素为增量文本片段）
     */
    Flux<String> stream(List<LlmMessage> messages, String scene, Long userId);

    /** 消息载体 */
    record LlmMessage(String role, String content) {
        public static LlmMessage system(String content) { return new LlmMessage("system", content); }
        public static LlmMessage user(String content)   { return new LlmMessage("user", content); }
        public static LlmMessage assistant(String content) { return new LlmMessage("assistant", content); }
    }
}
