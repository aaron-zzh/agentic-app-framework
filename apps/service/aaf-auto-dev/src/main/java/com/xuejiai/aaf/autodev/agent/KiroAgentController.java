package com.xuejiai.aaf.autodev.agent;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Kiro Agent 运行时端点。
 *
 * <p>通过 ProcessBuilder 调用 kiro-cli，将输出转为 AG-UI SSE 事件流。
 */
@Tag(name = "Kiro Agent")
@RestController
@RequestMapping("/api/autodev/kiro")
public class KiroAgentController {

    private static final Logger log = LoggerFactory.getLogger(KiroAgentController.class);
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;
    private static final List<String> DEFAULT_AGENTS =
            List.of("kiro_default", "product", "architect", "developer-service", "tester", "qa");

    private final AutodevSessionRepository sessionRepository;
    private final AutodevMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Value("${aaf.autodev.kiro.work-dir:#{null}}")
    private String workDir;

    public KiroAgentController(
            AutodevSessionRepository sessionRepository,
            AutodevMessageRepository messageRepository,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    // ========== 请求/响应 DTO ==========

    /** Kiro 运行请求 */
    public record KiroRunRequest(
            @NotBlank String threadId,
            @NotNull List<KiroMessage> messages,
            String agentRole,
            Object state) {}

    /** Kiro 消息 */
    public record KiroMessage(String role, String content) {}

    // ========== 端点 ==========

    @Operation(summary = "启动 Kiro Agent 运行")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run")
    public SseEmitter run(
            @RequestBody @Valid KiroRunRequest request,
            @RequestParam(required = false) String agentRole) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var runId = UUID.randomUUID().toString();

        String role =
                agentRole != null
                        ? agentRole
                        : request.agentRole() != null ? request.agentRole() : "kiro_default";
        var session = new AutodevSession();
        session.setSessionId(request.threadId());
        session.setAgentRole(role);
        session.setStatus("active");
        sessionRepository.save(session);

        String userMessage =
                request.messages().stream()
                        .filter(m -> "user".equals(m.role()))
                        .reduce((first, second) -> second)
                        .map(KiroMessage::content)
                        .orElse("");

        // 保存用户消息
        messageRepository.save(AutodevMessage.of(session.getId(), "user", "human", userMessage));

        Thread.startVirtualThread(() -> executeKiro(emitter, runId, userMessage, session));
        return emitter;
    }

    @Operation(summary = "获取可用 Agent 角色列表")
    @GetMapping("/agents")
    public Result<List<String>> listAgents() {
        Path agentsDir = resolveWorkDir().resolve(".kiro/agents");
        if (!Files.isDirectory(agentsDir)) {
            return Result.success(DEFAULT_AGENTS);
        }
        try (var stream = Files.list(agentsDir)) {
            var agents =
                    stream.filter(
                                    p ->
                                            p.toString().endsWith(".yaml")
                                                    || p.toString().endsWith(".yml"))
                            .map(
                                    p -> {
                                        String name = p.getFileName().toString();
                                        int dot = name.lastIndexOf('.');
                                        return dot > 0 ? name.substring(0, dot) : name;
                                    })
                            .collect(Collectors.toList());
            return Result.success(agents.isEmpty() ? DEFAULT_AGENTS : agents);
        } catch (IOException e) {
            log.warn("扫描 .kiro/agents/ 目录失败：{}", e.getMessage());
            return Result.success(DEFAULT_AGENTS);
        }
    }

    @Operation(summary = "获取历史会话列表")
    @GetMapping("/sessions")
    public Result<List<AutodevSessionVO>> listSessions() {
        var sessions =
                sessionRepository.findTop20ByOrderByCreateTimeDesc().stream()
                        .map(
                                s ->
                                        new AutodevSessionVO(
                                                s.getId(),
                                                s.getSessionId(),
                                                s.getAgentRole(),
                                                s.getStatus(),
                                                s.getCreateTime()))
                        .collect(Collectors.toList());
        return Result.success(sessions);
    }

    // ========== 内部方法 ==========

    private void executeKiro(
            SseEmitter emitter, String runId, String userMessage, AutodevSession session) {
        try {
            Path workPath = resolveWorkDir();
            // 消息通过位置参数传入，--no-interactive 禁止交互式等待用户输入
            var cmd =
                    new ArrayList<>(
                            List.of("kiro-cli", "chat", "--no-interactive", "--trust-all-tools"));
            if (!"kiro_default".equals(session.getAgentRole()) && session.getAgentRole() != null) {
                cmd.add("--agent");
                cmd.add(session.getAgentRole());
            }
            cmd.add(userMessage);

            var pb = new ProcessBuilder(cmd);
            pb.directory(workPath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 发送 RUN_STARTED
            sendEvent(emitter, runId, "RUN_STARTED", null);

            var messageId = UUID.randomUUID().toString();
            sendEvent(emitter, runId, "TEXT_MESSAGE_START", null);

            // 逐行读取 stdout 转为 SSE 事件，同时收集完整输出
            var fullOutput = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullOutput.append(line).append("\n");
                    sendTextContent(emitter, runId, messageId, line + "\n");
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                sendEvent(emitter, runId, "RUN_ERROR", "执行超时");
            } else {
                sendEvent(emitter, runId, "TEXT_MESSAGE_END", null);
                sendEvent(emitter, runId, "RUN_FINISHED", null);
                // 保存 AI 回复消息
                if (!fullOutput.isEmpty()) {
                    messageRepository.save(
                            AutodevMessage.of(
                                    session.getId(), "assistant", "kiro", fullOutput.toString()));
                }
            }

            // 更新会话状态
            session.setStatus(finished ? "completed" : "timeout");
            sessionRepository.save(session);

            emitter.complete();
        } catch (Exception e) {
            log.error("Kiro Agent 执行失败: runId={}", runId, e);
            try {
                sendEvent(emitter, runId, "RUN_ERROR", e.getMessage());
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // 客户端已断开
            }
            session.setStatus("failed");
            sessionRepository.save(session);
        }
    }

    private void sendEvent(SseEmitter emitter, String runId, String type, String error) {
        try {
            var map = new LinkedHashMap<String, Object>();
            map.put("type", type);
            map.put("runId", runId);
            if (error != null) map.put("error", error);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(map)));
        } catch (IOException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private void sendTextContent(SseEmitter emitter, String runId, String messageId, String delta) {
        try {
            var map = new LinkedHashMap<String, Object>();
            map.put("type", "TEXT_MESSAGE_CONTENT");
            map.put("runId", runId);
            map.put("messageId", messageId);
            map.put("delta", delta);
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(map)));
        } catch (IOException e) {
            log.debug("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private Path resolveWorkDir() {
        if (workDir != null && !workDir.isBlank()) {
            return Path.of(workDir);
        }
        return Path.of(System.getProperty("user.dir"));
    }
}
