package com.xuejiai.aaf.module.examples.agentscope.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.examples.agentscope.service.AgentScopeExampleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope 综合示例接口。
 *
 * <p>覆盖 AgentScope 全部核心特性：
 *
 * <ol>
 *   <li>基础聊天 — {@code POST /basic-chat}
 *   <li>工具调用 + Hook/Tracing — {@code POST /tool-calling}（含 ObservationHook + TokenMeteringHook + 可选 Langfuse）
 *   <li>Supervisor 多智能体 — {@code POST /supervisor}
 *   <li>Pipeline 顺序管道 — {@code POST /pipeline}
 *   <li>MsgHub 辩论 — {@code POST /debate}
 *   <li>Session 持久化 — {@code POST /session-chat}
 *   <li>MCP 工具集成 — {@code POST /mcp-tool}（需配置 MCP Server URL）
 *   <li>RAG 知识库聊天 — {@code POST /rag-chat}（需 DashScope Embedding）
 *   <li>Plan 任务规划 — {@code POST /plan-chat}
 *   <li>Realtime TTS — {@code POST /tts}（需 DashScope API，返回 WAV 音频）
 *   <li>AG-UI 流式 — {@code POST /agui/run/{agentId}}（由 agentscope-agui-spring-boot-starter 提供）
 * </ol>
 *
 * <p>启用条件：{@code aaf.examples.agentscope.enabled=true}
 */
@Slf4j
@Tag(name = "AgentScope 示例")
@RestController
@RequestMapping("/api/examples/agentscope")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "aaf.examples.agentscope.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class AgentScopeExampleController {

    private final AgentScopeExampleService exampleService;

    public record ChatRequest(String input) {}

    public record DebateRequest(String topic, int rounds) {}

    public record SessionChatRequest(String sessionId, String input) {}

    @Operation(summary = "① 基础聊天", description = "无工具，直接与 Agent 对话")
    @PostMapping("/basic-chat")
    public Result<String> basicChat(@RequestBody ChatRequest req) {
        return Result.success(exampleService.basicChat(req.input()));
    }

    @Operation(summary = "② 工具调用", description = "Agent 使用数学计算和时间查询工具（@Tool/@ToolParam）")
    @PostMapping("/tool-calling")
    public Result<String> toolCalling(@RequestBody ChatRequest req) {
        return Result.success(exampleService.toolCalling(req.input()));
    }

    @Operation(summary = "③ Supervisor 多智能体", description = "主 Agent 通过 subAgent 委托给日历子 Agent")
    @PostMapping("/supervisor")
    public Result<String> supervisor(@RequestBody ChatRequest req) {
        return Result.success(exampleService.supervisorChat(req.input()));
    }

    @Operation(summary = "④ Pipeline 顺序管道", description = "自然语言 → SQL 生成 → SQL 质量评分（多 Agent 串联）")
    @PostMapping("/pipeline")
    public Result<Map<String, Object>> pipeline(@RequestBody ChatRequest req) {
        return Result.success(exampleService.pipelineRun(req.input()));
    }

    @Operation(summary = "⑤ MsgHub 辩论", description = "两个辩手 Agent 通过 MsgHub 广播消息互相感知，主持人汇总结论")
    @PostMapping("/debate")
    public Result<Map<String, Object>> debate(@RequestBody DebateRequest req) {
        return Result.success(exampleService.debate(req.topic(), req.rounds()));
    }

    @Operation(
            summary = "⑥ Session 持久化",
            description = "相同 sessionId 多次调用延续上下文，使用 JsonSession 保存/恢复对话历史")
    @PostMapping("/session-chat")
    public Result<Map<String, Object>> sessionChat(@RequestBody SessionChatRequest req) {
        return Result.success(exampleService.sessionChat(req.sessionId(), req.input()));
    }

    @Operation(
            summary = "⑦ MCP 工具集成",
            description = "通过 MCP 协议动态发现并调用外部工具服务器，需配置 aaf.examples.agentscope.mcp.server-url")
    @PostMapping("/mcp-tool")
    public Result<String> mcpTool(@RequestBody ChatRequest req) {
        return Result.success(exampleService.mcpToolCall(req.input()));
    }

    @Operation(
            summary = "⑧ RAG 知识库聊天",
            description = "Generic RAG 模式：每次推理前自动检索知识库注入上下文，减少幻觉")
    @PostMapping("/rag-chat")
    public Result<String> ragChat(@RequestBody ChatRequest req) {
        return Result.success(exampleService.ragChat(req.input()));
    }

    @Operation(
            summary = "⑨ Plan 任务规划",
            description = "PlanNotebook：Agent 将复杂任务分解为子任务，逐步执行并追踪进度")
    @PostMapping("/plan-chat")
    public Result<String> planChat(@RequestBody ChatRequest req) {
        return Result.success(exampleService.planChat(req.input()));
    }

    @Operation(
            summary = "⑩ Realtime TTS 语音合成",
            description = "WebSocket 流式 TTS，保存 WAV 到服务端 ~/.aaf/examples/tts/，返回文件路径。需配置 DASHSCOPE_API_KEY")
    @PostMapping("/tts")
    public Result<String> tts(@RequestBody ChatRequest req) {
        return Result.success(exampleService.textToSpeech(req.input()));
    }
}
