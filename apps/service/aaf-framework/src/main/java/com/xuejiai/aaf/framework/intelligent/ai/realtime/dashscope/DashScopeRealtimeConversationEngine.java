package com.xuejiai.aaf.framework.intelligent.ai.realtime.dashscope;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversation;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationConfig;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationConfig.FunctionTool;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationConfig.Generation;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationConfig.Modality;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationConfig.TurnDetection;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationEngine;
import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationEvent;

import lombok.extern.slf4j.Slf4j;

/** 基于阿里云 DashScope SDK 的实时多模态对话引擎。 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeRealtimeConversationEngine implements RealtimeConversationEngine {

    private static final String MODEL = "qwen3.5-omni-plus-realtime";

    private final String apiKey;

    public DashScopeRealtimeConversationEngine(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public RealtimeConversation openConversation(
            RealtimeConversationConfig config, Consumer<RealtimeConversationEvent> eventConsumer) {
        var param = OmniRealtimeParam.builder().model(MODEL).apikey(apiKey).build();
        var closed = new AtomicBoolean();

        var callback =
                new OmniRealtimeCallback() {
                    @Override
                    public void onOpen() {
                        log.debug("[RealtimeConversation] 连接已建立");
                    }

                    @Override
                    public void onEvent(JsonObject message) {
                        logReceivedEvent(message);
                        var event = parseEvent(message);
                        if (event != null) {
                            eventConsumer.accept(event);
                        }
                    }

                    @Override
                    public void onClose(int code, String reason) {
                        closed.set(true);
                        log.info("[RealtimeConversation] 连接关闭: code={}, reason={}", code, reason);
                        eventConsumer.accept(
                                new RealtimeConversationEvent(
                                        "closed", reason, null, Map.of("code", code)));
                    }
                };

        var conversation = new OmniRealtimeConversation(param, callback);
        try {
            conversation.connect();
            conversation.updateSession(toSdkConfig(config));
        } catch (Exception exception) {
            closeAfterOpenFailure(conversation, exception);
            throw new IllegalStateException(
                    "DashScope 实时对话连接失败: " + exception.getMessage(), exception);
        }

        log.info("[RealtimeConversation] 会话已创建: model={}", MODEL);
        return new DashScopeRealtimeConversation(
                conversation,
                closed,
                config.modalities().stream().map(this::toSdkModality).toList());
    }

    private void closeAfterOpenFailure(
            OmniRealtimeConversation conversation, Exception openFailure) {
        try {
            conversation.close();
        } catch (RuntimeException closeFailure) {
            openFailure.addSuppressed(closeFailure);
        }
    }

    private OmniRealtimeConfig toSdkConfig(RealtimeConversationConfig config) {
        var configBuilder =
                OmniRealtimeConfig.builder()
                        .enableInputAudioTranscription(config.enableInputAudioTranscription())
                        .modalities(config.modalities().stream().map(this::toSdkModality).toList());

        if (config.voice() != null) {
            configBuilder.voice(config.voice());
        }

        var turnDetection = config.turnDetection();
        configBuilder.enableTurnDetection(turnDetection != null);
        if (turnDetection != null) {
            applyTurnDetection(configBuilder, turnDetection);
        }

        var parameters = buildSessionParameters(config);
        if (!parameters.isEmpty()) {
            configBuilder.parameters(parameters);
        }
        return configBuilder.build();
    }

    private OmniRealtimeModality toSdkModality(Modality modality) {
        return switch (modality) {
            case TEXT -> OmniRealtimeModality.TEXT;
            case AUDIO -> OmniRealtimeModality.AUDIO;
        };
    }

    private void applyTurnDetection(
            OmniRealtimeConfig.OmniRealtimeConfigBuilder<?, ?> configBuilder,
            TurnDetection turnDetection) {
        configBuilder.turnDetectionType(turnDetection.type().value());
        if (turnDetection.threshold() != null) {
            configBuilder.turnDetectionThreshold(turnDetection.threshold());
        }
        if (turnDetection.prefixPaddingMs() != null) {
            configBuilder.prefixPaddingMs(turnDetection.prefixPaddingMs());
        }
        if (turnDetection.silenceDurationMs() != null) {
            configBuilder.turnDetectionSilenceDurationMs(turnDetection.silenceDurationMs());
        }
        if (turnDetection.idleTimeoutMs() != null) {
            configBuilder.turnDetectionParam(
                    Map.of("idle_timeout_ms", turnDetection.idleTimeoutMs()));
        }
    }

    private Map<String, Object> buildSessionParameters(RealtimeConversationConfig config) {
        var parameters = new LinkedHashMap<String, Object>();
        if (config.instructions() != null) {
            parameters.put("instructions", config.instructions());
        }
        applyGeneration(parameters, config.generation());
        if (config.enableSearch()) {
            parameters.put("enable_search", true);
        }
        if (!config.searchOptions().isEmpty()) {
            parameters.put("search_options", config.searchOptions());
        }
        if (!config.tools().isEmpty()) {
            parameters.put("tools", config.tools().stream().map(this::toSdkTool).toList());
        }
        return parameters;
    }

    private void applyGeneration(Map<String, Object> parameters, Generation generation) {
        if (generation.temperature() != null) {
            parameters.put("temperature", generation.temperature());
        }
        if (generation.topP() != null) {
            parameters.put("top_p", generation.topP());
        }
        if (generation.topK() != null) {
            parameters.put("top_k", generation.topK());
        }
        if (generation.maxTokens() != null) {
            parameters.put("max_tokens", generation.maxTokens());
        }
        if (generation.repetitionPenalty() != null) {
            parameters.put("repetition_penalty", generation.repetitionPenalty());
        }
        if (generation.presencePenalty() != null) {
            parameters.put("presence_penalty", generation.presencePenalty());
        }
        if (generation.seed() != null) {
            parameters.put("seed", generation.seed());
        }
    }

    private Map<String, Object> toSdkTool(FunctionTool tool) {
        var function = new LinkedHashMap<String, Object>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", tool.parameters());
        return Map.of("type", "function", "function", function);
    }

    /** 记录供应商下行事件类型，跳过高频增量事件，用于排查事件是否到达。 */
    private void logReceivedEvent(JsonObject message) {
        if (!log.isDebugEnabled()) {
            return;
        }
        var type = stringValue(message, "type", null);
        if (type == null || type.endsWith(".delta")) {
            return;
        }
        var item = objectValue(message, "item");
        var extra = new LinkedHashMap<String, Object>();
        addResponseId(extra, message);
        log.debug(
                "[RealtimeConversation] 收到事件: type={}, responseId={}, itemId={}, itemType={}, callId={}",
                type,
                extra.get("responseId"),
                item == null
                        ? stringValue(message, "item_id", null)
                        : stringValue(item, "id", null),
                item == null ? null : stringValue(item, "type", null),
                item == null
                        ? stringValue(message, "call_id", null)
                        : stringValue(item, "call_id", null));
    }

    private RealtimeConversationEvent parseEvent(JsonObject message) {
        var type = stringValue(message, "type", null);
        if (type == null) {
            return null;
        }

        return switch (type) {
            case "session.created" -> event("session_created");
            case "session.updated" -> event("session_updated");
            case "input_audio_buffer.committed" -> event("input_audio_committed");
            case "input_audio_buffer.cleared" -> event("input_audio_cleared");
            case "input_audio_buffer.speech_started" -> event("speech_started");
            case "input_audio_buffer.speech_stopped" -> event("speech_stopped");
            case "conversation.item.input_audio_transcription.delta" ->
                    inputTranscriptionDeltaEvent(message);
            case "conversation.item.input_audio_transcription.completed" ->
                    textEvent("input_transcript", stringValue(message, "transcript", ""));
            case "conversation.item.input_audio_transcription.failed" ->
                    errorEvent("input_transcription_failed", message);
            case "response.created" -> responseEvent("response_created", message);
            case "response.text.delta" ->
                    responseTextEvent("text_delta", stringValue(message, "delta", ""), message);
            case "response.text.done" -> textEvent("text_done", stringValue(message, "text", ""));
            case "response.audio_transcript.delta" ->
                    responseTextEvent(
                            "audio_transcript_delta", stringValue(message, "delta", ""), message);
            case "response.audio.delta" -> audioDeltaEvent(message);
            case "response.audio_transcript.done" ->
                    responseTextEvent(
                            "transcript_done", stringValue(message, "transcript", ""), message);
            case "response.audio.done" -> responseEvent("audio_done", message);
            case "response.function_call_arguments.delta" -> functionArgumentsDeltaEvent(message);
            case "response.function_call_arguments.done" -> functionArgumentsDoneEvent(message);
            case "response.done" -> responseEvent("response_done", message);
            case "error" -> errorEvent("error", message);
            default -> null;
        };
    }

    private RealtimeConversationEvent inputTranscriptionDeltaEvent(JsonObject message) {
        var stableText = stringValue(message, "text", "");
        var stash = stringValue(message, "stash", "");
        var extra = new LinkedHashMap<String, Object>();
        putStringIfPresent(extra, "itemId", message, "item_id");
        putStringIfPresent(extra, "language", message, "language");
        putStringIfPresent(extra, "emotion", message, "emotion");
        extra.put("stableText", stableText);
        extra.put("stash", stash);
        return new RealtimeConversationEvent(
                "input_transcription_delta", stableText + stash, null, Map.copyOf(extra));
    }

    private RealtimeConversationEvent audioDeltaEvent(JsonObject message) {
        var encodedAudio = stringValue(message, "delta", "");
        var audio = encodedAudio.isEmpty() ? new byte[0] : Base64.getDecoder().decode(encodedAudio);
        return new RealtimeConversationEvent("audio_delta", null, audio, responseExtra(message));
    }

    private RealtimeConversationEvent functionArgumentsDeltaEvent(JsonObject message) {
        var extra = new LinkedHashMap<String, Object>();
        extra.put("callId", stringValue(message, "call_id", ""));
        extra.put("delta", stringValue(message, "delta", ""));
        addResponseId(extra, message);
        return new RealtimeConversationEvent(
                "function_call_arguments_delta", null, null, Map.copyOf(extra));
    }

    private RealtimeConversationEvent functionArgumentsDoneEvent(JsonObject message) {
        var extra = new LinkedHashMap<String, Object>();
        extra.put("callId", stringValue(message, "call_id", ""));
        extra.put("name", stringValue(message, "name", ""));
        extra.put("arguments", stringValue(message, "arguments", ""));
        addResponseId(extra, message);
        return new RealtimeConversationEvent(
                "function_call_arguments_done", null, null, Map.copyOf(extra));
    }

    private RealtimeConversationEvent responseEvent(String type, JsonObject message) {
        return new RealtimeConversationEvent(type, null, null, responseExtra(message));
    }

    private RealtimeConversationEvent responseTextEvent(
            String type, String text, JsonObject message) {
        return new RealtimeConversationEvent(type, text, null, responseExtra(message));
    }

    private Map<String, Object> responseExtra(JsonObject message) {
        var extra = new LinkedHashMap<String, Object>();
        addResponseId(extra, message);
        return extra.isEmpty() ? null : Map.copyOf(extra);
    }

    private void addResponseId(Map<String, Object> target, JsonObject message) {
        var responseId = stringValue(message, "response_id", null);
        if (responseId == null) {
            var response = objectValue(message, "response");
            responseId = response == null ? null : stringValue(response, "id", null);
        }
        if (responseId != null) {
            target.put("responseId", responseId);
        }
    }

    private RealtimeConversationEvent errorEvent(String eventType, JsonObject message) {
        var error = objectValue(message, "error");
        if (error == null) {
            return textEvent(eventType, "未知错误");
        }

        var extra = new LinkedHashMap<String, Object>();
        putStringIfPresent(extra, "code", error, "code");
        putStringIfPresent(extra, "param", error, "param");
        putStringIfPresent(extra, "errorType", error, "type");
        return new RealtimeConversationEvent(
                eventType,
                stringValue(error, "message", "未知错误"),
                null,
                extra.isEmpty() ? null : Map.copyOf(extra));
    }

    private void putStringIfPresent(
            Map<String, Object> target, String targetKey, JsonObject source, String sourceKey) {
        var value = stringValue(source, sourceKey, null);
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    private JsonObject objectValue(JsonObject object, String field) {
        if (!object.has(field)
                || object.get(field).isJsonNull()
                || !object.get(field).isJsonObject()) {
            return null;
        }
        return object.getAsJsonObject(field);
    }

    private String stringValue(JsonObject object, String field, String defaultValue) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return defaultValue;
        }
        return object.get(field).getAsString();
    }

    private RealtimeConversationEvent event(String type) {
        return new RealtimeConversationEvent(type, null, null, null);
    }

    private RealtimeConversationEvent textEvent(String type, String text) {
        return new RealtimeConversationEvent(type, text, null, null);
    }

    /** DashScope SDK 实时对话句柄。 */
    private static class DashScopeRealtimeConversation implements RealtimeConversation {

        private final OmniRealtimeConversation conversation;
        private final AtomicBoolean closed;
        private final List<OmniRealtimeModality> responseModalities;

        DashScopeRealtimeConversation(
                OmniRealtimeConversation conversation,
                AtomicBoolean closed,
                List<OmniRealtimeModality> responseModalities) {
            this.conversation = conversation;
            this.closed = closed;
            this.responseModalities = List.copyOf(responseModalities);
        }

        @Override
        public void sendAudio(byte[] audio) {
            runWhenOpen(() -> conversation.appendAudio(Base64.getEncoder().encodeToString(audio)));
        }

        @Override
        public void sendText(String text) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text 不能为空");
            }
            var item = new JsonObject();
            item.addProperty("type", "message");
            item.addProperty("role", "user");
            var content = new JsonArray();
            var input = new JsonObject();
            input.addProperty("type", "input_text");
            input.addProperty("text", text);
            content.add(input);
            item.add("content", content);
            runWhenOpen(() -> conversation.createItem(item));
        }

        @Override
        public void sendImage(byte[] image) {
            runWhenOpen(() -> conversation.appendVideo(Base64.getEncoder().encodeToString(image)));
        }

        @Override
        public void commit() {
            runWhenOpen(conversation::commit);
        }

        @Override
        public void submitFunctionOutput(String callId, String output) {
            if (callId == null || callId.isBlank()) {
                throw new IllegalArgumentException("callId 不能为空");
            }
            Objects.requireNonNull(output, "output 不能为空");

            // 与官方示例一致，为工具结果条目显式生成 item id
            var item = new JsonObject();
            item.addProperty("id", "item_" + UUID.randomUUID().toString().replace("-", ""));
            item.addProperty("type", "function_call_output");
            item.addProperty("call_id", callId);
            item.addProperty("output", output);
            log.debug(
                    "[RealtimeConversation] 发送工具结果: callId={}, outputLength={}",
                    callId,
                    output.length());
            runWhenOpen(() -> conversation.createItem(item));
        }

        @Override
        public void createResponse() {
            log.debug(
                    "[RealtimeConversation] 发送 response.create: modalities={}", responseModalities);
            // 显式携带会话输出模态，工具结果回传后的响应才会按会话配置生成语音
            runWhenOpen(() -> conversation.createResponse(null, responseModalities));
        }

        @Override
        public void cancelResponse() {
            runWhenOpen(conversation::cancelResponse);
        }

        @Override
        public String getSessionId() {
            return conversation.getSessionId();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                conversation.close();
            }
        }

        private void runWhenOpen(Runnable action) {
            if (closed.get()) {
                return;
            }
            try {
                action.run();
            } catch (RuntimeException exception) {
                if (isConversationClosed(exception)) {
                    closed.set(true);
                }
                throw exception;
            }
        }

        private static boolean isConversationClosed(RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if ("conversation is already closed!".equals(cause.getMessage())) {
                    return true;
                }
            }
            return false;
        }
    }
}
