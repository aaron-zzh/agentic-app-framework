package com.xuejiai.aaf.module.examples.agentscope.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope 综合示例服务（v2 兼容版）。
 *
 * <p>支持：① 基础聊天、② 工具调用、③ Supervisor、④ Pipeline、⑦ MCP。 已移除：⑤ MsgHub、⑥ Session、⑧ RAG、⑨ Plan、⑩ TTS（v2
 * 不再支持）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class AgentScopeExampleService {

    @Qualifier("basicChatAgent")
    private final ReActAgent basicChatAgent;

    @Qualifier("toolCallingAgent")
    private final ReActAgent toolCallingAgent;

    @Qualifier("supervisorAgent")
    private final ReActAgent supervisorAgent;

    @Qualifier("sqlGeneratorAgent")
    private final ReActAgent sqlGeneratorAgent;

    @Qualifier("sqlRaterAgent")
    private final ReActAgent sqlRaterAgent;

    @Qualifier("mcpToolAgent")
    private final ReActAgent mcpToolAgent;

    @Qualifier("exampleDashScopeModel")
    private final Model model;

    @Value("${spring.ai.dashscope.api-key:${DASHSCOPE_API_KEY:}}")
    private String dashScopeApiKey;

    // ── 1. 基础聊天 ──────────────────────────────────────────────────────────

    public String basicChat(String userInput) {
        log.debug("基础聊天：{}", userInput);
        return extractText(basicChatAgent.call(buildUserMsg(userInput)).block());
    }

    // ── 2. 工具调用 ──────────────────────────────────────────────────────────

    public String toolCalling(String userInput) {
        log.debug("工具调用：{}", userInput);
        return extractText(toolCallingAgent.call(buildUserMsg(userInput)).block());
    }

    // ── 3. Supervisor 多智能体 ────────────────────────────────────────────────

    public String supervisorChat(String userInput) {
        log.debug("Supervisor：{}", userInput);
        return extractText(supervisorAgent.call(buildUserMsg(userInput)).block());
    }

    // ── 4. Pipeline 顺序管道 ──────────────────────────────────────────────────

    public Map<String, Object> pipelineRun(String userInput) {
        log.debug("Pipeline：{}", userInput);
        String sql = extractText(sqlGeneratorAgent.call(buildUserMsg(userInput)).block());
        String score =
                extractText(
                        sqlRaterAgent
                                .call(buildUserMsg("用户原始请求：" + userInput + "\n\n生成的 SQL：\n" + sql))
                                .block());
        return Map.of("input", userInput, "sql", sql, "score", score);
    }

    // ── 7. MCP 工具集成 ───────────────────────────────────────────────────────

    public String mcpToolCall(String userInput) {
        log.debug("MCP 工具调用：{}", userInput);
        return extractText(mcpToolAgent.call(buildUserMsg(userInput)).block());
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    private Msg buildUserMsg(String text) {
        return Msg.builder().name("user").textContent(text).build();
    }

    private String extractText(Msg msg) {
        if (msg == null) return "（无响应）";
        String text = msg.getTextContent();
        return text != null ? text : "（无响应）";
    }
}
