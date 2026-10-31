package com.xuejiai.aaf.module.ai.aigc.realtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversation;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationConfig;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationConfig.TurnDetection;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationEngine;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationEvent;
import com.xuejiai.aaf.module.ai.aigc.realtime.context.ResolvedRealtimeContext;
import com.xuejiai.aaf.module.ai.aigc.realtime.service.RealtimeKnowledgeSearchService;
import com.xuejiai.aaf.module.device.api.DeviceRealtimeContextClient;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/** Omni Realtime v1 共享状态机，所有会话状态转换都经单一 FIFO 执行器。 */
@Slf4j
final class DefaultRealtimeConversationSession implements RealtimeConversationSession {

    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> KNOWLEDGE_FIELDS = Set.of("query", "topK");

    private final String sessionId = UUID.randomUUID().toString();
    private final ResolvedRealtimeContext context;
    private final long contextResolveMs;
    private final boolean knowledgeEnabled;
    private final RealtimeSessionTransport transport;
    private final RealtimeConversationEngine engine;
    private final RealtimeKnowledgeSearchService knowledgeSearchService;
    private final DeviceRealtimeContextClient deviceContextClient;
    private final SerialExecutor serial = new SerialExecutor();
    private final ExecutorService knowledgeExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private State state = State.INITIALIZING;
    private RealtimeConversation conversation;
    private AutoCloseable toolScope;
    private Future<?> knowledgeFuture;
    private long responseGeneration;
    private Long expectedResponseGeneration;
    private String activeResponseId;
    private final Set<String> cancelledResponseIds = new java.util.HashSet<>();
    private boolean responseStartAllowed;
    private long knowledgeSearchGeneration;
    private String activeKnowledgeCallId;
    private boolean audioStarted;
    private boolean hasPendingAudio;
    private boolean audioCommitted;
    private long responseStartedAt;
    private final long openedAt = System.nanoTime();

    DefaultRealtimeConversationSession(
            ResolvedRealtimeContext context,
            long contextResolveMs,
            boolean knowledgeEnabled,
            RealtimeSessionTransport transport,
            RealtimeConversationEngine engine,
            RealtimeKnowledgeSearchService knowledgeSearchService,
            DeviceRealtimeContextClient deviceContextClient) {
        this.context = Objects.requireNonNull(context, "context 不能为空");
        this.contextResolveMs = contextResolveMs;
        this.knowledgeEnabled = knowledgeEnabled;
        this.transport = Objects.requireNonNull(transport, "transport 不能为空");
        this.engine = Objects.requireNonNull(engine, "engine 不能为空");
        this.knowledgeSearchService =
                Objects.requireNonNull(knowledgeSearchService, "knowledgeSearchService 不能为空");
        this.deviceContextClient =
                Objects.requireNonNull(deviceContextClient, "deviceContextClient 不能为空");
    }

    void initialize() {
        var openedAt = System.nanoTime();
        try {
            if (context.deviceContext() != null) {
                toolScope = deviceContextClient.openToolScope(context.deviceContext());
            }
            var config =
                    RealtimeConversationConfig.audio(
                            null,
                            context.assistantContext().instructions(),
                            "server".equals(context.endpointing())
                                    ? TurnDetection.serverVad()
                                    : null,
                            RealtimeConversationConfig.Generation.defaults(),
                            knowledgeEnabled
                                    ? List.of(knowledgeSearchService.toolDefinition())
                                    : List.of());
            conversation = engine.openConversation(config, this::onEngineEvent);
            state = State.READY;
            sendReady();
            log.info(
                    "Omni Realtime 引擎打开完成: sessionId={}, callerType={}, endpointing={}, durationMs={}",
                    sessionId,
                    callerType(),
                    context.endpointing(),
                    elapsedMillis(openedAt));
        } catch (RuntimeException exception) {
            closeInternal(RealtimeCloseReason.ENGINE_ERROR, false);
            throw exception;
        }
    }

    @Override
    public void receiveControl(RealtimeClientControl control) {
        serial.execute(() -> handleControl(control));
    }

    @Override
    public void receiveAudio(byte[] pcm16Le) {
        var copy = pcm16Le == null ? null : pcm16Le.clone();
        serial.execute(() -> handleAudio(copy));
    }

    @Override
    public void cancelResponse() {
        receiveControl(new RealtimeClientControl.ResponseCancel());
    }

    @Override
    public void close() {
        serial.execute(() -> closeInternal(RealtimeCloseReason.NORMAL, true));
    }

    private void handleControl(RealtimeClientControl control) {
        if (state == State.CLOSED || state == State.CLOSING) {
            return;
        }
        if (control == null) {
            protocolError("INVALID_EVENT", "控制事件不能为空");
            return;
        }
        switch (control) {
            case RealtimeClientControl.InputText input -> handleText(input.text());
            case RealtimeClientControl.InputImage input ->
                    handleImage(input.mimeType(), input.data());
            case RealtimeClientControl.InputAudioCommit ignored -> handleAudioCommit();
            case RealtimeClientControl.ResponseCreate ignored -> handleResponseCreate();
            case RealtimeClientControl.ResponseCancel ignored -> handleResponseCancel();
            case RealtimeClientControl.SessionClose ignored ->
                    closeInternal(RealtimeCloseReason.NORMAL, true);
        }
    }

    private void handleAudio(byte[] pcm16Le) {
        if (!requireReady("INPUT_NOT_READY", "会话尚未 ready")) {
            return;
        }
        if (pcm16Le == null || pcm16Le.length == 0) {
            protocolError("INVALID_AUDIO", "PCM 音频不能为空");
            return;
        }
        if (state != State.READY) {
            nonFatalError("RESPONSE_ACTIVE", "当前响应尚未结束");
            return;
        }
        if (expectedResponseGeneration != null && !responseStartAllowed) {
            nonFatalError("RESPONSE_ACTIVE", "已取消响应尚待引擎确认");
            return;
        }
        conversation.sendAudio(pcm16Le);
        if ("server".equals(context.endpointing()) && !responseStartAllowed) {
            responseGeneration++;
            expectedResponseGeneration = responseGeneration;
            responseStartAllowed = true;
        }
        hasPendingAudio = true;
        audioCommitted = false;
    }

    private void handleText(String text) {
        if (!requireReady("INPUT_NOT_READY", "会话尚未 ready") || !requireIdleResponse()) {
            return;
        }
        if (text == null || text.isBlank()) {
            protocolError("INVALID_TEXT", "text 必须为非空字符串");
            return;
        }
        conversation.sendText(text.trim());
        createResponse();
    }

    private void handleImage(String mimeType, byte[] data) {
        if (!requireReady("INPUT_NOT_READY", "会话尚未 ready") || !requireIdleResponse()) {
            return;
        }
        if (!validImage(mimeType, data)) {
            protocolError("INVALID_IMAGE", "图片格式或载荷无效");
            return;
        }
        conversation.sendImage(data);
        createResponse();
    }

    private static boolean validImage(String mimeType, byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_IMAGE_BYTES) {
            return false;
        }
        return switch (mimeType) {
            case "image/jpeg" ->
                    data.length >= 3
                            && data[0] == (byte) 0xFF
                            && data[1] == (byte) 0xD8
                            && data[2] == (byte) 0xFF;
            case "image/png" ->
                    data.length >= 8
                            && data[0] == (byte) 0x89
                            && data[1] == 0x50
                            && data[2] == 0x4E
                            && data[3] == 0x47
                            && data[4] == 0x0D
                            && data[5] == 0x0A
                            && data[6] == 0x1A
                            && data[7] == 0x0A;
            default -> false;
        };
    }

    private void handleAudioCommit() {
        if (!requireReady("INPUT_NOT_READY", "会话尚未 ready")) {
            return;
        }
        if (!"client".equals(context.endpointing())) {
            protocolError("UNSUPPORTED_EVENT", "浏览器不允许 input_audio.commit");
            return;
        }
        if (state != State.READY || audioCommitted || !hasPendingAudio) {
            nonFatalError("INVALID_STATE", "当前音频不能提交");
            return;
        }
        conversation.commit();
        hasPendingAudio = false;
        audioCommitted = true;
    }

    private void handleResponseCreate() {
        if (!requireReady("INPUT_NOT_READY", "会话尚未 ready")) {
            return;
        }
        if (!"client".equals(context.endpointing())) {
            protocolError("UNSUPPORTED_EVENT", "浏览器不允许 response.create");
            return;
        }
        if (state != State.READY || !audioCommitted) {
            nonFatalError("INVALID_STATE", "response.create 必须紧随成功的 input_audio.commit");
            return;
        }
        if (expectedResponseGeneration != null) {
            nonFatalError("RESPONSE_ACTIVE", "已取消响应尚待引擎确认");
            return;
        }
        createResponse();
        audioCommitted = false;
    }

    private void handleResponseCancel() {
        if (state == State.READY) {
            var hasUnconfirmedResponse = expectedResponseGeneration != null;
            invalidateResponse();
            if (hasUnconfirmedResponse) {
                conversation.cancelResponse();
            }
            sendEvent("response.cancelled", Map.of());
            return;
        }
        if (state != State.RESPONDING) {
            return;
        }
        cancelKnowledgeSearch();
        invalidateResponse();
        conversation.cancelResponse();
        state = State.READY;
        audioStarted = false;
        sendEvent("response.cancelled", Map.of());
    }

    private void createResponse() {
        beginResponse();
        expectedResponseGeneration = responseGeneration;
        conversation.createResponse();
        sendEvent("response.created", Map.of());
    }

    private void beginResponse() {
        if (activeResponseId != null) {
            cancelledResponseIds.add(activeResponseId);
        }
        responseGeneration++;
        activeResponseId = null;
        responseStartAllowed = true;
        state = State.RESPONDING;
        audioStarted = false;
        responseStartedAt = System.nanoTime();
    }

    private void invalidateResponse() {
        if (activeResponseId != null) {
            cancelledResponseIds.add(activeResponseId);
        }
        responseGeneration++;
        activeResponseId = null;
        responseStartAllowed = false;
    }

    private void onEngineEvent(RealtimeConversationEvent event) {
        serial.execute(() -> handleEngineEvent(event));
    }

    private void handleEngineEvent(RealtimeConversationEvent event) {
        if (event == null || state == State.CLOSED || state == State.CLOSING) {
            return;
        }
        switch (event.type()) {
            case "speech_started" -> sendEvent("input_audio.speech_started", Map.of());
            case "speech_stopped" -> sendEvent("input_audio.speech_stopped", Map.of());
            case "input_transcription_delta" ->
                    sendTextEvent("input_audio.transcript.delta", event.text());
            case "input_transcript" ->
                    sendTextEvent("input_audio.transcript.completed", event.text());
            case "response_created" -> handleEngineResponseCreated(event);
            case "text_delta", "audio_transcript_delta" -> {
                if (!isCurrentResponse(event) || event.text() == null || event.text().isBlank()) {
                    return;
                }
                emitFirstResponseDuration();
                sendTextEvent("response.text.delta", event.text());
            }
            case "audio_delta" -> {
                if (!isCurrentResponse(event)
                        || event.audio() == null
                        || event.audio().length == 0) {
                    return;
                }
                emitFirstResponseDuration();
                if (!audioStarted) {
                    audioStarted = true;
                    sendEvent("response.audio.started", Map.of());
                }
                transport.sendAudio(event.audio());
            }
            case "audio_done" -> {
                if (isCurrentResponse(event)) {
                    completeAudio();
                }
            }
            case "response_done" -> {
                if (isCurrentResponse(event)) {
                    completeResponse();
                }
            }
            case "function_call_arguments_done" -> {
                if (isCurrentResponse(event)) {
                    handleKnowledgeCall(event);
                }
            }
            case "error", "input_transcription_failed" -> nonFatalError("ENGINE_ERROR", "实时引擎处理失败");
            case "closed" -> closeInternal(RealtimeCloseReason.ENGINE_ERROR, true);
            default -> {
                // 供应商事件未列入 v1 协议时不转发。
            }
        }
    }

    private void handleEngineResponseCreated(RealtimeConversationEvent event) {
        var responseId = responseId(event);
        if (responseId == null || cancelledResponseIds.contains(responseId)) {
            return;
        }
        var expectedGeneration = expectedResponseGeneration;
        if (expectedGeneration == null) {
            cancelledResponseIds.add(responseId);
            return;
        }
        expectedResponseGeneration = null;
        if (expectedGeneration != responseGeneration) {
            cancelledResponseIds.add(responseId);
            return;
        }
        if (state == State.READY) {
            if (!responseStartAllowed) {
                cancelledResponseIds.add(responseId);
                return;
            }
            state = State.RESPONDING;
            activeResponseId = responseId;
            responseStartAllowed = false;
            audioStarted = false;
            responseStartedAt = System.nanoTime();
            sendEvent("response.created", Map.of());
            return;
        }
        if (state == State.RESPONDING && activeResponseId == null && responseStartAllowed) {
            activeResponseId = responseId;
            responseStartAllowed = false;
            return;
        }
        cancelledResponseIds.add(responseId);
    }

    private boolean isCurrentResponse(RealtimeConversationEvent event) {
        return state == State.RESPONDING
                && activeResponseId != null
                && activeResponseId.equals(responseId(event));
    }

    private static String responseId(RealtimeConversationEvent event) {
        if (event.extra() == null) {
            return null;
        }
        var value = event.extra().get("responseId");
        return value instanceof String responseId && !responseId.isBlank() ? responseId : null;
    }

    private void handleKnowledgeCall(RealtimeConversationEvent event) {
        if (state != State.RESPONDING
                || event.extra() == null
                || !RealtimeKnowledgeSearchService.TOOL_NAME.equals(event.extra().get("name"))) {
            return;
        }
        if (knowledgeFuture != null || !knowledgeEnabled) {
            nonFatalError("KNOWLEDGE_UNAVAILABLE", "当前会话不能执行知识检索");
            return;
        }
        var callId = event.extra().get("callId");
        if (!(callId instanceof String value) || value.isBlank()) {
            nonFatalError("KNOWLEDGE_UNAVAILABLE", "知识检索调用无效");
            return;
        }
        var arguments = event.extra().get("arguments");
        var startedAt = System.nanoTime();
        var responseGeneration = this.responseGeneration;
        var searchGeneration = ++knowledgeSearchGeneration;
        activeKnowledgeCallId = value;
        sendEvent("knowledge_search.started", Map.of());
        knowledgeFuture =
                knowledgeExecutor.submit(
                        () -> {
                            var result = searchKnowledge(arguments);
                            serial.execute(
                                    () ->
                                            completeKnowledgeSearch(
                                                    value,
                                                    responseGeneration,
                                                    searchGeneration,
                                                    result,
                                                    startedAt));
                        });
    }

    private RealtimeKnowledgeSearchService.SearchResponse searchKnowledge(Object arguments) {
        try {
            var query = parseKnowledgeQuery(arguments);
            return knowledgeSearchService.search(
                    context.identity(), context.assistantContext(), query.query(), query.topK());
        } catch (RuntimeException exception) {
            return RealtimeKnowledgeSearchService.SearchResponse.failure("SEARCH_FAILED", "知识检索失败");
        }
    }

    private void completeKnowledgeSearch(
            String callId,
            long expectedResponseGeneration,
            long expectedSearchGeneration,
            RealtimeKnowledgeSearchService.SearchResponse result,
            long startedAt) {
        if (state != State.RESPONDING
                || responseGeneration != expectedResponseGeneration
                || knowledgeSearchGeneration != expectedSearchGeneration
                || !callId.equals(activeKnowledgeCallId)) {
            return;
        }
        knowledgeFuture = null;
        activeKnowledgeCallId = null;
        var status = result.success() ? "success" : "failed";
        var durationMs = elapsedMillis(startedAt);
        sendEvent(
                "knowledge_search.completed",
                Map.of(
                        "status", status,
                        "durationMs", durationMs,
                        "resultCount", result.results().size()));
        log.info(
                "Omni Realtime 知识检索完成: sessionId={}, callerType={}, status={}, durationMs={}, resultCount={}",
                sessionId,
                callerType(),
                status,
                durationMs,
                result.results().size());
        try {
            conversation.submitFunctionOutput(callId, JsonUtils.toJsonString(result));
            beginResponse();
            expectedResponseGeneration = responseGeneration;
            conversation.createResponse();
        } catch (RuntimeException exception) {
            closeInternal(RealtimeCloseReason.ENGINE_ERROR, true);
        }
    }

    private KnowledgeQuery parseKnowledgeQuery(Object rawArguments) {
        if (!(rawArguments instanceof String arguments) || arguments.isBlank()) {
            throw new IllegalArgumentException("知识检索参数无效");
        }
        JsonNode node = JsonUtils.readTreeStrict(arguments);
        if (!node.isObject()) {
            throw new IllegalArgumentException("知识检索参数必须为对象");
        }
        var fields = new java.util.LinkedHashSet<String>();
        node.propertyNames().forEach(fields::add);
        if (!KNOWLEDGE_FIELDS.containsAll(fields)
                || !node.has("query")
                || !node.get("query").isString()) {
            throw new IllegalArgumentException("知识检索参数无效");
        }
        Integer topK = null;
        if (node.has("topK")) {
            if (!node.get("topK").isIntegralNumber()) {
                throw new IllegalArgumentException("知识检索 topK 无效");
            }
            topK = node.get("topK").asInt();
        }
        return new KnowledgeQuery(node.get("query").asString(), topK);
    }

    private void completeAudio() {
        if (audioStarted) {
            transport.sendEvent(new RealtimeServerEvent("response.audio.done", Map.of()));
            audioStarted = false;
        }
    }

    private void completeResponse() {
        completeAudio();
        if (state == State.RESPONDING) {
            state = State.READY;
            activeResponseId = null;
            responseStartAllowed = false;
            log.info(
                    "Omni Realtime 响应完成: sessionId={}, callerType={}, durationMs={}",
                    sessionId,
                    callerType(),
                    elapsedMillis(responseStartedAt));
            sendEvent("response.completed", Map.of());
        }
    }

    private boolean requireReady(String code, String message) {
        if (state == State.INITIALIZING) {
            nonFatalError(code, message);
            return false;
        }
        return state != State.CLOSED && state != State.CLOSING;
    }

    private boolean requireIdleResponse() {
        if (state != State.READY) {
            nonFatalError("RESPONSE_ACTIVE", "当前响应尚未结束");
            return false;
        }
        if (expectedResponseGeneration != null) {
            nonFatalError("RESPONSE_ACTIVE", "已取消响应尚待引擎确认");
            return false;
        }
        return true;
    }

    private void protocolError(String code, String message) {
        sendError(code, message, true);
        closeInternal(RealtimeCloseReason.PROTOCOL_ERROR, true);
    }

    private void nonFatalError(String code, String message) {
        sendError(code, message, false);
    }

    private void sendReady() {
        transport.sendEvent(
                new RealtimeServerEvent(
                        "ready",
                        Map.of(
                                "protocol",
                                "omni-realtime.v1",
                                "sessionId",
                                sessionId,
                                "endpointing",
                                context.endpointing(),
                                "contextResolveMs",
                                contextResolveMs,
                                "knowledgeEnabled",
                                knowledgeEnabled,
                                "inputAudioFormat",
                                Map.of(
                                        "encoding",
                                        "pcm_s16le",
                                        "sampleRateHz",
                                        16000,
                                        "channels",
                                        1),
                                "outputAudioFormat",
                                Map.of(
                                        "encoding",
                                        "pcm_s16le",
                                        "sampleRateHz",
                                        24000,
                                        "channels",
                                        1))));
        log.info(
                "Omni Realtime 会话就绪: sessionId={}, callerType={}, endpointing={}, contextDurationMs={}",
                sessionId,
                callerType(),
                context.endpointing(),
                elapsedMillis(openedAt));
    }

    private void sendTextEvent(String type, String text) {
        if (text != null && !text.isEmpty()) {
            sendEvent(type, Map.of("text", text));
        }
    }

    private void sendEvent(String type, Map<String, Object> data) {
        if (transport.isOpen()) {
            transport.sendEvent(new RealtimeServerEvent(type, data));
        }
    }

    private void sendError(String code, String message, boolean fatal) {
        sendEvent("error", Map.of("code", code, "message", message, "fatal", fatal));
    }

    private void emitFirstResponseDuration() {
        if (responseStartedAt == 0) {
            return;
        }
        var startedAt = responseStartedAt;
        responseStartedAt = 0;
        var durationMs = elapsedMillis(startedAt);
        sendEvent("response.latency", Map.of("durationMs", durationMs));
        log.info(
                "Omni Realtime 首响应: sessionId={}, callerType={}, durationMs={}",
                sessionId,
                callerType(),
                durationMs);
    }

    private void cancelKnowledgeSearch() {
        knowledgeSearchGeneration++;
        activeKnowledgeCallId = null;
        if (knowledgeFuture != null) {
            knowledgeFuture.cancel(true);
            knowledgeFuture = null;
        }
    }

    private void closeInternal(RealtimeCloseReason reason, boolean notifyTransport) {
        if (state == State.CLOSED || state == State.CLOSING) {
            return;
        }
        state = State.CLOSING;
        invalidateResponse();
        cancelKnowledgeSearch();
        try {
            if (conversation != null) {
                conversation.cancelResponse();
                conversation.close();
            }
        } catch (RuntimeException exception) {
            log.debug("Omni Realtime 引擎关闭异常: sessionId={}", sessionId);
        }
        closeToolScope();
        knowledgeExecutor.shutdownNow();
        serial.close();
        state = State.CLOSED;
        log.info(
                "Omni Realtime 会话关闭: sessionId={}, callerType={}, reason={}, durationMs={}",
                sessionId,
                callerType(),
                reason,
                elapsedMillis(openedAt));
        if (notifyTransport && transport.isOpen()) {
            transport.sendEvent(new RealtimeServerEvent("closed", Map.of("reason", reason.name())));
            transport.close(reason);
        }
    }

    private void closeToolScope() {
        if (toolScope == null) {
            return;
        }
        try {
            toolScope.close();
        } catch (Exception exception) {
            log.warn("Omni Realtime 工具范围关闭失败: sessionId={}", sessionId);
        } finally {
            toolScope = null;
        }
    }

    private String callerType() {
        return context.deviceContext() == null ? "web" : "device";
    }

    private static long elapsedMillis(long startedAt) {
        return startedAt == 0 ? 0 : (System.nanoTime() - startedAt) / 1_000_000;
    }

    private enum State {
        INITIALIZING,
        READY,
        RESPONDING,
        CLOSING,
        CLOSED
    }

    private record KnowledgeQuery(String query, Integer topK) {}

    /** 基于虚拟线程的 FIFO 串行执行器，不在跨会话间共享锁。 */
    private static final class SerialExecutor implements AutoCloseable {

        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final ReentrantLock lock = new ReentrantLock();
        private final Deque<Runnable> queue = new ArrayDeque<>();
        private boolean running;
        private boolean closed;

        void execute(Runnable task) {
            lock.lock();
            try {
                if (closed) {
                    return;
                }
                queue.addLast(task);
                if (!running) {
                    running = true;
                    executor.execute(this::drain);
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            lock.lock();
            try {
                if (closed) {
                    return;
                }
                closed = true;
                queue.clear();
                executor.shutdown();
            } finally {
                lock.unlock();
            }
        }

        private void drain() {
            while (true) {
                Runnable task;
                lock.lock();
                try {
                    task = queue.pollFirst();
                    if (task == null) {
                        running = false;
                        return;
                    }
                } finally {
                    lock.unlock();
                }
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    log.warn(
                            "Omni Realtime 串行任务失败: reason={}",
                            exception.getClass().getSimpleName());
                }
            }
        }
    }
}
