package com.xuejiai.aaf.module.ai.agui;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
 */
public class AafAguiRestController extends AguiRestController {

    private static final Logger logger = LoggerFactory.getLogger(AafAguiRestController.class);

    private final AguiRequestProcessor processor;
    private final Session agentSession;
    private final AguiEventEncoder encoder = new AguiEventEncoder();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final long sseTimeout;

    public AafAguiRestController(
            AguiMvcController mvc,
            AguiProperties props,
            AguiRequestProcessor processor,
            Session agentSession) {
        super(mvc, props.getPathPrefix(), props.isEnablePathRouting());
        this.processor = processor;
        this.agentSession = agentSession;
        this.sseTimeout = props.getSseTimeout() > 0 ? props.getSseTimeout() : 600000L;
    }

    @Override
    @PostMapping(
            value = "${agentscope.agui.path-prefix:/agui}/runs",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
    public SseEmitter runWithAgentId(
            @PathVariable String agentId,
            @RequestBody RunAgentInput input,
            @RequestHeader(value = "X-Agent-Id", required = false) String agentIdHeader) {
        return handle(input, agentIdHeader, agentId);
    }

    private SseEmitter handle(RunAgentInput input, String headerAgentId, String pathAgentId) {
        var emitter = new SseEmitter(sseTimeout);
        var threadId = input.getThreadId();
        var runId = input.getRunId();
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
