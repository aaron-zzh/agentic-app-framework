package com.xuejiai.aaf.module.examples.agentscope.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeMultiAgentFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.pipeline.MsgHub;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.tool.Toolkit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AgentScope 综合示例服务。
 *
 * <p>覆盖 AgentScope 核心特性：
 * <ol>
 *   <li>基础聊天（ReActAgent）
 *   <li>工具调用（@Tool/@ToolParam）
 *   <li>Supervisor 多智能体（subAgent 委托）
 *   <li>Pipeline 顺序管道（多 Agent 串联）
 *   <li>MsgHub 辩论（广播消息，多 Agent 协作）
 *   <li>Session 持久化（JsonSession save/load）
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aaf.examples.agentscope.enabled", havingValue = "true", matchIfMissing = false)
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

    /** 共享模型，用于 Session 示例动态创建 Agent */
    @Qualifier("exampleDashScopeModel")
    private final Model model;

    /** DashScope API Key，用于辩论示例动态创建带 MultiAgentFormatter 的模型 */
    @Value("${spring.ai.dashscope.api-key:${DASHSCOPE_API_KEY:}}")
    private String dashScopeApiKey;

    /** Session 存储目录 */
    private static final Session SESSION_STORE = new JsonSession(
            Paths.get(System.getProperty("user.home"), ".aaf", "examples", "sessions"));

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
        String score = extractText(sqlRaterAgent.call(
                buildUserMsg("用户原始请求：" + userInput + "\n\n生成的 SQL：\n" + sql)).block());
        return Map.of("input", userInput, "sql", sql, "score", score);
    }

    // ── 5. MsgHub 辩论（多 Agent 广播协作）────────────────────────────────────

    /**
     * 使用 MsgHub 广播机制，让两个辩手 Agent 围绕话题辩论，主持人汇总结论。
     *
     * <p>每轮：正方发言 → 反方发言（自动收到正方消息）→ 主持人在 Hub 外汇总。
     *
     * @param topic 辩论话题
     * @param rounds 辩论轮数（建议 1-3）
     * @return 每轮发言记录 + 主持人结论
     */
    public Map<String, Object> debate(String topic, int rounds) {
        log.debug("MsgHub 辩论：topic={}, rounds={}", topic, rounds);

        // 使用 MultiAgentFormatter 让 Agent 感知其他参与者的发言
        String key = StringUtils.hasText(dashScopeApiKey)
                ? dashScopeApiKey
                : System.getenv("DASHSCOPE_API_KEY");
        var multiModel = DashScopeChatModel.builder()
                .apiKey(key)
                .modelName("qwen-plus")
                .formatter(new DashScopeMultiAgentFormatter())
                .build();

        ReActAgent affirmative = ReActAgent.builder()
                .name("正方")
                .sysPrompt("你是辩论赛正方，支持以下观点：" + topic + "。每次发言简洁有力，不超过3句话。")
                .model(multiModel)
                .memory(new InMemoryMemory())
                .toolkit(new Toolkit())
                .build();

        ReActAgent negative = ReActAgent.builder()
                .name("反方")
                .sysPrompt("你是辩论赛反方，反对以下观点：" + topic + "。每次发言简洁有力，不超过3句话。")
                .model(multiModel)
                .memory(new InMemoryMemory())
                .toolkit(new Toolkit())
                .build();

        // 主持人使用普通模型，在 Hub 外汇总，不参与广播
        ReActAgent moderator = ReActAgent.builder()
                .name("主持人")
                .sysPrompt("你是辩论赛主持人，负责在每轮结束后客观总结双方观点，并在最后给出结论。")
                .model(model)
                .memory(new InMemoryMemory())
                .toolkit(new Toolkit())
                .build();

        List<Map<String, String>> transcript = new ArrayList<>();
        String conclusion = "";

        for (int i = 1; i <= Math.max(1, Math.min(rounds, 3)); i++) {
            // MsgHub：正方和反方互相广播消息
            try (MsgHub hub = MsgHub.builder()
                    .name("DebateHub-Round" + i)
                    .participants(affirmative, negative)
                    .announcement(Msg.builder().name("system")
                            .textContent("第" + i + "轮辩论开始，话题：" + topic).build())
                    .enableAutoBroadcast(true)
                    .build()) {

                hub.enter().block();

                Msg affMsg = affirmative.call().block();
                Msg negMsg = negative.call().block();

                transcript.add(Map.of(
                        "round", String.valueOf(i),
                        "正方", extractText(affMsg),
                        "反方", extractText(negMsg)));
            }

            // 主持人在 Hub 外汇总本轮（不参与广播）
            Msg summary = moderator.call(buildUserMsg("请总结第" + i + "轮辩论双方观点。")).block();
            conclusion = extractText(summary);
        }

        // 最终结论
        Msg finalMsg = moderator.call(buildUserMsg("辩论结束，请给出最终结论。")).block();
        conclusion = extractText(finalMsg);

        return Map.of("topic", topic, "transcript", transcript, "conclusion", conclusion);
    }

    // ── 6. Session 持久化 ─────────────────────────────────────────────────────

    /**
     * 带持久化的对话：自动加载历史会话，对话后保存。
     *
     * <p>同一 sessionId 多次调用会延续上下文。
     *
     * @param sessionId 会话 ID（相同 ID 延续历史）
     * @param userInput 用户输入
     * @return Agent 回复
     */
    public Map<String, Object> sessionChat(String sessionId, String userInput) {
        log.debug("Session 对话：sessionId={}", sessionId);

        // 每次请求创建新 Agent 实例，通过 Session 恢复记忆
        ReActAgent agent = ReActAgent.builder()
                .name("SessionAssistant")
                .sysPrompt("你是一个有记忆的助手，能记住之前的对话内容。")
                .model(model)
                .memory(new InMemoryMemory())
                .toolkit(new Toolkit())
                .build();

        // 加载已有会话
        boolean isNew = !SESSION_STORE.exists(SimpleSessionKey.of(sessionId));
        if (!isNew) {
            agent.loadFrom(SESSION_STORE, sessionId);
            log.debug("已加载会话：{}", sessionId);
        }

        int historySize = agent.getMemory().getMessages().size();

        // 执行对话
        Msg response = agent.call(buildUserMsg(userInput)).block();
        String reply = extractText(response);

        // 保存会话
        agent.saveTo(SESSION_STORE, sessionId);

        return Map.of(
                "sessionId", sessionId,
                "isNew", isNew,
                "historyMessages", historySize,
                "reply", reply);
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
