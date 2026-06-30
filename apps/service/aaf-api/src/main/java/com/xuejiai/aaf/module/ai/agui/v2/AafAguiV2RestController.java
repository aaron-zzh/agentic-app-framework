/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.module.ai.agui.v2;

import java.io.IOException;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.agentscope.runtime.AafContextHolder;
import com.xuejiai.aaf.framework.agentscope.runtime.AgentCapabilityContext;
import com.xuejiai.aaf.framework.agentscope.runtime.ConversationContextResolver;
import com.xuejiai.aaf.framework.protection.RateLimit;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.agentscope.core.agui.AguiException;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.agui.processor.AguiRequestProcessor;
import io.agentscope.core.agui.registry.AguiAgentRegistry;
import io.agentscope.spring.boot.agui.common.AguiProperties;
import io.agentscope.spring.boot.agui.common.DefaultAgentResolver;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import io.agentscope.spring.boot.agui.mvc.AguiMvcController;
import io.agentscope.spring.boot.agui.mvc.AguiRestController;
import reactor.core.Disposable;

/**
 * AAF 自定义 AG-UI REST 控制器——覆盖 starter 的 {@link AguiRestController}。
 *
 * <h2>为什么必须覆盖</h2>
 *
 * <p>agentscope 本身支持通过 {@code RuntimeContext.builder().userId("xxx").build()} 向 agent 注入用户身份， 但
 * AG-UI starter（{@link io.agentscope.core.agui.adapter.AguiAgentAdapter}）调用的是 {@code
 * agent.stream(msgs, options)}，<b>没有传递 RuntimeContext</b>，用户上下文在桥接层被丢弃。 这是
 * agentscope-agui-spring-boot-starter 2.0-RC4 的设计缺陷，尚未修复。
 *
 * <p>因此，如果直接使用 starter 默认的 {@link AguiRestController}，工具里调用 {@link AafContextHolder} 永远拿不到
 * userId，知识库搜索、记忆读写、积分扣减全部失效。
 *
 * <p>本控制器通过在 executor 线程调 {@link AguiRequestProcessor#process} 之前先注入 {@link AafContextHolder}
 * ThreadLocal，补齐 starter 遗漏的用户上下文传递。 待 starter 升级支持 RuntimeContext 传递后，可替换为官方方案并移除 ThreadLocal。
 *
 * <h2>AAF 增强功能</h2>
 *
 * <ol>
 *   <li><b>用户上下文注入</b>：从 JWT（{@link OperatorContext}）+ forwardedProps + conversation 表三路兜底， 写入
 *       {@link AafContextHolder} ThreadLocal，供下游工具读取 userId / conversationId / knowledgeBaseId
 *   <li><b>访客路由与限流</b>：未登录用户自动路由到 {@code customer-service} Agent， 并用 Redis 累计对话轮次，超过 {@value
 *       #GUEST_MAX_ROUNDS} 轮拒绝
 *   <li><b>积分结算分类</b>：按 agentId 写入 {@link AgentCapabilityContext}，区分 copywriting / chat
 * </ol>
 *
 * <h2>处理流程</h2>
 *
 * <ol>
 *   <li>controller 线程捕获 userId（JWT 过滤器已写入 {@link OperatorContext}）
 *   <li>解析完整上下文：forwardedProps 优先 → conversation 表兜底 → JWT 兜底
 *   <li>未登录用户路由到 customer-service，检查访客轮次限制
 *   <li>{@code executor.submit(() -> { AafContextHolder.set(ctx); processor.process(input); })}
 *   <li>emitter 生命周期钩子里 {@link AafContextHolder#clear} 防 ThreadLocal 泄漏
 * </ol>
 */
public class AafAguiV2RestController extends AguiRestController {

    private static final Logger log = LoggerFactory.getLogger(AafAguiV2RestController.class);

    private final AguiRequestProcessor processor;
    private final AguiEventEncoder encoder = new AguiEventEncoder();

    private final long sseTimeout;
    private final OperatorContext operatorContext;
    private final ConversationContextResolver contextResolver;
    private final StringRedisTemplate redisTemplate;

    /** 访客（未登录）每个 anonymousId 最多允许的对话轮次 */
    private static final int GUEST_MAX_ROUNDS = 20;

    private final ExecutorService executor =
            Executors.newCachedThreadPool(
                    r -> {
                        var t = new Thread(r, "aaf-agui-runner");
                        t.setDaemon(true);
                        return t;
                    });

    public AafAguiV2RestController(
            AguiMvcController mvcController,
            AguiAgentRegistry registry,
            ThreadSessionManager sessionManager,
            AguiProperties props,
            OperatorContext operatorContext,
            ConversationContextResolver contextResolver,
            StringRedisTemplate redisTemplate) {
        super(mvcController, props.getPathPrefix(), props.isEnablePathRouting());
        this.operatorContext = operatorContext;
        this.contextResolver = contextResolver;
        this.redisTemplate = redisTemplate;
        this.sseTimeout = props.getSseTimeout() > 0 ? props.getSseTimeout() : 600_000L;

        // 构造自己的 processor，等价于 AguiMvcController 内部那一份
        var resolver =
                DefaultAgentResolver.builder()
                        .registry(registry)
                        .sessionManager(sessionManager)
                        .serverSideMemory(props.isServerSideMemory())
                        .build();
        var config =
                AguiAdapterConfig.builder()
                        .toolMergeMode(props.getDefaultToolMergeMode())
                        .runTimeout(props.getRunTimeout())
                        .emitStateEvents(props.isEmitStateEvents())
                        .emitToolCallArgs(props.isEmitToolCallArgs())
                        .enableReasoning(props.isEnableReasoning())
                        .defaultAgentId(props.getDefaultAgentId())
                        .build();
        this.processor =
                AguiRequestProcessor.builder().agentResolver(resolver).config(config).build();
    }

    @Override
    @PostMapping(
            value = "${agentscope.agui.path-prefix:/agui}/run",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limit = 20, windowSeconds = 60, prefix = "agui-run", message = "对话请求过于频繁，请稍后再试")
    public SseEmitter run(
            @RequestBody RunAgentInput input,
            @RequestHeader(
                            value = "${agentscope.agui.agent-id-header:X-Agent-Id}",
                            required = false)
                    String agentIdHeader) {
        return handle(input, agentIdHeader, null);
    }

    @Override
    @PostMapping(
            value = "${agentscope.agui.path-prefix:/agui}/run/{agentId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limit = 20, windowSeconds = 60, prefix = "agui-run", message = "对话请求过于频繁，请稍后再试")
    public SseEmitter runWithAgentId(
            @PathVariable String agentId,
            @RequestBody RunAgentInput input,
            @RequestHeader(
                            value = "${agentscope.agui.agent-id-header:X-Agent-Id}",
                            required = false)
                    String agentIdHeader) {
        return handle(input, agentIdHeader, agentId);
    }

    private SseEmitter handle(RunAgentInput input, String headerAgentId, String pathAgentId) {
        // 1. 在 controller 线程捕获用户身份
        Long jwtUserId = operatorContext.currentUserId().orElse(null);
        Map<String, Object> forwardedProps = input.getForwardedProps();
        String threadId = input.getThreadId();
        String runId = input.getRunId();

        log.debug(
                "[AGUI-DEBUG] jwtUserId={} threadId={} forwardedProps.userId={}",
                jwtUserId,
                threadId,
                forwardedProps != null ? forwardedProps.get("userId") : null);

        // 2. 解析完整上下文（forwardedProps 优先 → conversation 表兜底 → JWT 兜底）
        AafContextHolder.AafContext ctx =
                contextResolver.resolve(threadId, forwardedProps, jwtUserId);

        // 3. 未登录用户（userId 为空）自动路由到 customer-service Agent，并检查对话轮次限制
        String resolvedAgentId = pathAgentId;
        if (ctx.userId() == null) {
            resolvedAgentId = "customer-service";
            // 检查访客对话轮次（以 anonymousId 为 key，永久累计，超限拒绝）
            String anonymousId =
                    forwardedProps != null ? (String) forwardedProps.get("anonymousId") : null;
            if (anonymousId != null && !anonymousId.isBlank()) {
                String roundKey = "guest:agui:rounds:" + anonymousId;
                Long rounds = redisTemplate.opsForValue().increment(roundKey);
                if (rounds != null && rounds > GUEST_MAX_ROUNDS) {
                    log.info("[AAF-AGUI] 访客超出对话限制 anonymousId={} rounds={}", anonymousId, rounds);
                    SseEmitter limited = new SseEmitter(5_000L);
                    sendErrorAndComplete(
                            limited,
                            threadId,
                            runId,
                            "访客对话次数已达上限（" + GUEST_MAX_ROUNDS + "轮），请注册后继续使用");
                    return limited;
                }
            }
            log.debug("[AAF-AGUI] 未登录用户，路由到 customer-service agentId");
        }

        log.debug(
                "[AAF-AGUI] threadId={} runId={} agentId(resolved)={} userId={} convId={} kbId={}",
                threadId,
                runId,
                resolvedAgentId,
                ctx.userId(),
                ctx.conversationId(),
                ctx.knowledgeBaseId());

        // 3. 创建 SseEmitter
        SseEmitter emitter = new SseEmitter(sseTimeout);
        final String finalAgentId = resolvedAgentId;

        // 4. 在自有 executor 内 set ThreadLocal → 调 processor → subscribe events
        executor.submit(
                () -> {
                    AafContextHolder.set(ctx);
                    // 按 agentId 设置积分结算分类，文案类 Agent 写 copywriting，其余写 chat
                    AgentCapabilityContext.set(resolveCapability(finalAgentId));
                    Disposable subscription = null;
                    try {
                        AguiRequestProcessor.ProcessResult result =
                                processor.process(input, headerAgentId, finalAgentId);

                        emitter.onCompletion(
                                () -> log.debug("[AAF-AGUI] SSE completed runId={}", runId));
                        emitter.onTimeout(
                                () -> {
                                    log.info("[AAF-AGUI] SSE timeout runId={}", runId);
                                    result.agent().interrupt();
                                });
                        emitter.onError(
                                ex -> {
                                    log.info(
                                            "[AAF-AGUI] SSE error runId={} err={}",
                                            runId,
                                            ex == null ? "?" : ex.getMessage());
                                    result.agent().interrupt();
                                });

                        subscription =
                                result.events()
                                        .subscribe(
                                                event -> sendEvent(emitter, event),
                                                error -> {
                                                    log.error(
                                                            "[AAF-AGUI] run error runId={} err={}",
                                                            runId,
                                                            error.getMessage());
                                                    sendErrorAndComplete(
                                                            emitter,
                                                            threadId,
                                                            runId,
                                                            error.getMessage());
                                                },
                                                () -> {
                                                    try {
                                                        emitter.complete();
                                                    } catch (Exception ignore) {
                                                    }
                                                });
                    } catch (AguiException.AgentNotFoundException e) {
                        log.error("[AAF-AGUI] agent not found: {}", e.getMessage());
                        sendErrorAndComplete(emitter, threadId, runId, e.getMessage());
                    } catch (Exception e) {
                        log.error(
                                "[AAF-AGUI] 处理失败 threadId={} runId={} err={}",
                                threadId,
                                runId,
                                e.getMessage(),
                                e);
                        sendErrorAndComplete(emitter, threadId, runId, e.getMessage());
                    }
                });

        // 5. 终结时清理 ThreadLocal（虽然在 executor 线程，但 ThreadLocal 还是要清避免线程复用串号）
        Runnable clearCtx =
                () -> {
                    try {
                        AafContextHolder.clear();
                        AgentCapabilityContext.clear();
                    } catch (Exception ignore) {
                    }
                };
        emitter.onCompletion(clearCtx);
        emitter.onTimeout(clearCtx);
        emitter.onError(ex -> clearCtx.run());

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, AguiEvent event) {
        try {
            String json = encoder.encodeToJson(event);
            emitter.send(SseEmitter.event().data(json, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.debug("[AAF-AGUI] SSE 发送失败: {}", e.getMessage());
        }
    }

    private void sendErrorAndComplete(
            SseEmitter emitter, String threadId, String runId, String msg) {
        try {
            String errJson =
                    encoder.encodeToJson(
                            new AguiEvent.Raw(
                                    threadId, runId, Map.of("error", msg == null ? "error" : msg)));
            String finJson = encoder.encodeToJson(new AguiEvent.RunFinished(threadId, runId));
            emitter.send(SseEmitter.event().data(errJson, MediaType.APPLICATION_JSON));
            emitter.send(SseEmitter.event().data(finJson, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * 按 agentId 映射积分结算 capability 分类。 文案类 Agent（content-creation 等）写 "copywriting"，其余默认写 "chat"。
     */
    private static String resolveCapability(String agentId) {
        if (agentId == null) return "chat";
        return switch (agentId) {
            case "content-creation", "copywriting", "viral-copy" -> "copywriting";
            default -> "chat";
        };
    }
}
