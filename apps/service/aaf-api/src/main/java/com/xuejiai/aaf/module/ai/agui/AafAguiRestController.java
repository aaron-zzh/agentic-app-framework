package com.xuejiai.aaf.module.ai.agui;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.protection.RateLimit;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.processor.AguiRequestProcessor;
import io.agentscope.core.session.Session;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import io.agentscope.spring.boot.agui.mvc.AguiMvcController;
import io.agentscope.spring.boot.agui.mvc.AguiRestController;

/**
 * AAF 自定义 AG-UI 入口——覆盖 starter 的 {@link AguiRestController}（同类型 Bean 触发
 * {@code @ConditionalOnMissingBean} 压制默认实现），映射到 {@code /agui/runs}， 内部用注入了 {@link AafAgentResolver}
 * 的 {@link AguiRequestProcessor}， 从而在执行线程上设置上下文 + 冷启动播种历史 + 请求完成后持久化 Agent 状态。
 *
 * <p>限流策略（公开端点必备，{@code /api/agui/runs/**} 在 SecurityConfig 公开白名单）：
 *
 * <ul>
 *   <li>请求频率：每身份（登录用户/匿名 IP）每分钟 ≤ 20 次新会话请求（{@link RateLimit} AOP）
 *   <li>并发连接：每身份同时 ≤ 3 个活跃 SSE，超限直接发 error 事件并 complete
 * </ul>
 */
public class AafAguiRestController extends AguiRestController {

    private static final Logger logger = LoggerFactory.getLogger(AafAguiRestController.class);

    /** 每个身份允许的最大并发 SSE 连接数 */
    private static final int MAX_CONCURRENT_SSE = 3;

    /** Redis 计数 key 前缀 */
    private static final String SSE_CONN_KEY_PREFIX = "agui:sse-conn:";

    /**
     * 计数器 TTL（防泄漏）：远超单次 SSE 超时（默认 10 分钟），但在 onCompletion/onError/onTimeout 钩子失效时兜底自动过期，
     * 避免计数器一直占着导致用户被永久封禁。
     */
    private static final Duration COUNTER_TTL = Duration.ofMinutes(30);

    private final AguiRequestProcessor processor;
    private final Session agentSession;
    private final AguiEventEncoder encoder = new AguiEventEncoder();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final long sseTimeout;
    private final StringRedisTemplate redisTemplate;
    private final OperatorContext operatorContext;

    public AafAguiRestController(
            AguiMvcController mvc,
            AguiProperties props,
            AguiRequestProcessor processor,
            Session agentSession,
            StringRedisTemplate redisTemplate,
            OperatorContext operatorContext) {
        super(mvc, props.getPathPrefix(), props.isEnablePathRouting());
        this.processor = processor;
        this.agentSession = agentSession;
        this.sseTimeout = props.getSseTimeout() > 0 ? props.getSseTimeout() : 600000L;
        this.redisTemplate = redisTemplate;
        this.operatorContext = operatorContext;
    }

    @Override
    @PostMapping(
            value = "${agentscope.agui.path-prefix:/agui}/runs",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limit = 20, windowSeconds = 60, prefix = "agui-runs", message = "对话请求过于频繁，请稍后再试")
    public SseEmitter run(
            @RequestBody RunAgentInput input,
            @RequestHeader(value = "X-Agent-Id", required = false) String agentIdHeader) {
        return handle(input, agentIdHeader, null);
    }

    @Override
    @PostMapping(
            value = "${agentscope.agui.path-prefix:/agui}/runs/{agentId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limit = 20, windowSeconds = 60, prefix = "agui-runs", message = "对话请求过于频繁，请稍后再试")
    public SseEmitter runWithAgentId(
            @PathVariable String agentId,
            @RequestBody RunAgentInput input,
            @RequestHeader(value = "X-Agent-Id", required = false) String agentIdHeader) {
        return handle(input, agentIdHeader, agentId);
    }

    private SseEmitter handle(RunAgentInput input, String headerAgentId, String pathAgentId) {
        // 并发计数：incr 后超阈值则立即降回并拒绝；通过的连接在 emitter 终结时 decr。
        var identity = currentIdentity();
        var connKey = SSE_CONN_KEY_PREFIX + identity;
        Long count = redisTemplate.opsForValue().increment(connKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(connKey, COUNTER_TTL);
        }
        if (count != null && count > MAX_CONCURRENT_SSE) {
            redisTemplate.opsForValue().decrement(connKey);
            logger.warn(
                    "[AG-UI 并发限流] identity={} count={} max={}",
                    identity,
                    count,
                    MAX_CONCURRENT_SSE);
            return rejectWithError("并发对话连接已达上限（最多 " + MAX_CONCURRENT_SSE + " 个），请关闭其他对话窗口后再试");
        }

        var emitter = new SseEmitter(sseTimeout);
        var threadId = input.getThreadId();
        var runId = input.getRunId();

        // 注册连接释放钩子：Completion/Timeout/Error 任一触发都 decr，避免泄漏
        Runnable releaseConn =
                () -> {
                    try {
                        redisTemplate.opsForValue().decrement(connKey);
                    } catch (Exception ex) {
                        logger.warn("[AG-UI 并发计数] 释放失败 identity={}", identity, ex);
                    }
                };
        emitter.onCompletion(releaseConn);
        emitter.onTimeout(releaseConn);
        emitter.onError(ex -> releaseConn.run());

        executor.submit(
                () -> {
                    try {
                        var result = processor.process(input, headerAgentId, pathAgentId);
                        emitter.onTimeout(() -> result.agent().interrupt());
                        emitter.onError(ex -> result.agent().interrupt());
                        result.events()
                                .doFinally(signal -> saveAgentState(result.agent(), threadId))
                                .subscribe(
                                        event -> sendEvent(emitter, event),
                                        error ->
                                                sendErrorAndComplete(
                                                        emitter,
                                                        threadId,
                                                        runId,
                                                        error.getMessage()),
                                        emitter::complete);
                    } catch (Exception e) {
                        logger.error("AG-UI 请求处理失败: {}", e.getMessage());
                        sendErrorAndComplete(emitter, threadId, runId, e.getMessage());
                    } finally {
                        com.xuejiai.aaf.framework.intelligent.agent.context.AgentRunContextHolder
                                .clear();
                    }
                });
        return emitter;
    }

    /** 已登录走 userId 维度，匿名走 IP 维度（与 {@link RateLimit} 保持一致）。 */
    private String currentIdentity() {
        return operatorContext
                .currentUserId()
                .map(uid -> "u:" + uid)
                .orElseGet(() -> "ip:" + currentIp());
    }

    private String currentIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            var request = attrs.getRequest();
            var ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) ip = request.getHeader("X-Real-IP");
            if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
            return ip == null ? "unknown" : ip.split(",")[0].trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 立即返回一个写入错误事件并 complete 的 emitter，用于触发并发上限拒绝。 */
    private SseEmitter rejectWithError(String msg) {
        var emitter = new SseEmitter(0L);
        try {
            emitter.send(
                    SseEmitter.event()
                            .data(
                                    encoder.encodeToJson(
                                            new AguiEvent.Raw(null, null, Map.of("error", msg))),
                                    MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端已断开
        }
        return emitter;
    }

    /** 请求完成后持久化 Agent 状态到 Redis。 */
    private void saveAgentState(io.agentscope.core.agent.Agent agent, String threadId) {
        if (threadId != null && agent instanceof ReActAgent reactAgent) {
            try {
                reactAgent.saveTo(agentSession, threadId);
            } catch (Exception e) {
                logger.warn("Agent 状态持久化失败 [threadId={}]: {}", threadId, e.getMessage());
            }
        }
    }

    private void sendEvent(SseEmitter emitter, AguiEvent event) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .data(encoder.encodeToJson(event), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            logger.debug("SSE 发送失败: {}", e.getMessage());
        }
    }

    private void sendErrorAndComplete(
            SseEmitter emitter, String threadId, String runId, String msg) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .data(
                                    encoder.encodeToJson(
                                            new AguiEvent.Raw(
                                                    threadId,
                                                    runId,
                                                    Map.of("error", msg == null ? "error" : msg))),
                                    MediaType.APPLICATION_JSON));
            emitter.send(
                    SseEmitter.event()
                            .data(
                                    encoder.encodeToJson(
                                            new AguiEvent.RunFinished(threadId, runId)),
                                    MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }
}
