package com.xuejiai.aaf.module.examples.agentscope.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.examples.agentscope.config.ExampleRateLimiter;
import com.xuejiai.aaf.module.examples.agentscope.service.AgentScopeExampleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentScope 示例接口（v2 兼容版）。
 *
 * <p>支持：① 基础聊天、② 工具调用、③ Supervisor、④ Pipeline、⑦ MCP。 启用条件：{@code
 * aaf.examples.agentscope.enabled=true}
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
    private final ExampleRateLimiter rateLimiter;

    public record ChatRequest(String input) {}

    private void checkRate(HttpServletRequest request) {
        var ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        rateLimiter.check(ip);
    }

    @Operation(summary = "① 基础聊天")
    @PostMapping("/basic-chat")
    public Result<String> basicChat(@RequestBody ChatRequest req, HttpServletRequest http) {
        checkRate(http);
        return Result.success(exampleService.basicChat(req.input()));
    }

    @Operation(summary = "② 工具调用", description = "Agent 使用数学计算和时间查询工具（@Tool/@ToolParam）")
    @PostMapping("/tool-calling")
    public Result<String> toolCalling(@RequestBody ChatRequest req, HttpServletRequest http) {
        checkRate(http);
        return Result.success(exampleService.toolCalling(req.input()));
    }

    @Operation(summary = "③ Supervisor 多智能体", description = "主 Agent 通过 subAgent 委托给日历子 Agent")
    @PostMapping("/supervisor")
    public Result<String> supervisor(@RequestBody ChatRequest req, HttpServletRequest http) {
        checkRate(http);
        return Result.success(exampleService.supervisorChat(req.input()));
    }

    @Operation(summary = "④ Pipeline 顺序管道", description = "自然语言 → SQL 生成 → SQL 质量评分")
    @PostMapping("/pipeline")
    public Result<Map<String, Object>> pipeline(
            @RequestBody ChatRequest req, HttpServletRequest http) {
        checkRate(http);
        return Result.success(exampleService.pipelineRun(req.input()));
    }

    @Operation(summary = "⑦ MCP 工具集成", description = "通过 MCP 协议动态发现并调用外部工具服务器")
    @PostMapping("/mcp-tool")
    public Result<String> mcpTool(@RequestBody ChatRequest req, HttpServletRequest http) {
        checkRate(http);
        return Result.success(exampleService.mcpToolCall(req.input()));
    }
}
