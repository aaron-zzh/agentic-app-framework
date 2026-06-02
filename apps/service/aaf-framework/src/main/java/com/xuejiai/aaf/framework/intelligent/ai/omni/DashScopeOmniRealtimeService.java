package com.xuejiai.aaf.framework.intelligent.ai.omni;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于阿里云 DashScope SDK 的 Qwen-Omni 实时多模态服务实现。
 *
 * <p>通过 OmniRealtimeConversation 建立 WebSocket 连接，支持音频/视频双向流式交互。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeOmniRealtimeService implements OmniRealtimeService {

    private static final String DEFAULT_MODEL = "qwen3-omni-flash-realtime";

    private final String apiKey;

    public DashScopeOmniRealtimeService(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public OmniSession createSession(SessionConfig config, Consumer<OmniEvent> eventCallback) {
        var model = config.model() != null ? config.model() : DEFAULT_MODEL;

        var param = OmniRealtimeParam.builder().model(model).apikey(apiKey).build();

        var callback =
                new OmniRealtimeCallback() {
                    @Override
                    public void onOpen() {
                        log.debug("[OmniRealtime] 连接已建立");
                    }

                    @Override
                    public void onEvent(JsonObject message) {
                        var event = parseEvent(message);
                        if (event != null) {
                            eventCallback.accept(event);
                        }
                    }

                    @Override
                    public void onClose(int code, String reason) {
                        log.debug("[OmniRealtime] 连接关闭: code={}, reason={}", code, reason);
                        eventCallback.accept(
                                new OmniEvent("closed", reason, null, Map.of("code", code)));
                    }
                };

        var conversation = new OmniRealtimeConversation(param, callback);
        try {
            conversation.connect();
        } catch (Exception e) {
            throw new RuntimeException("Omni Realtime 连接失败: " + e.getMessage(), e);
        }

        // 配置会话
        var configBuilder =
                OmniRealtimeConfig.builder()
                        .enableTurnDetection(config.enableTurnDetection())
                        .enableInputAudioTranscription(config.enableInputAudioTranscription());

        if (config.voice() != null) {
            configBuilder.voice(config.voice());
        }

        // 设置输出模态
        if (config.modalities() != null && !config.modalities().isEmpty()) {
            var modalities =
                    config.modalities().stream()
                            .map(
                                    m ->
                                            "audio".equalsIgnoreCase(m)
                                                    ? OmniRealtimeModality.AUDIO
                                                    : OmniRealtimeModality.TEXT)
                            .toList();
            configBuilder.modalities(modalities);
        } else {
            configBuilder.modalities(
                    Arrays.asList(OmniRealtimeModality.TEXT, OmniRealtimeModality.AUDIO));
        }

        // 设置 instructions
        if (config.instructions() != null) {
            configBuilder.parameters(Map.of("instructions", config.instructions()));
        }

        conversation.updateSession(configBuilder.build());

        log.info("[OmniRealtime] 会话已创建: model={}", model);
        return new DashScopeOmniSession(conversation);
    }

    private OmniEvent parseEvent(JsonObject message) {
        if (!message.has("type")) return null;
        var type = message.get("type").getAsString();

        return switch (type) {
            case "response.audio_transcript.delta" -> {
                var delta = message.has("delta") ? message.get("delta").getAsString() : "";
                yield new OmniEvent("audio_transcript_delta", delta, null, null);
            }
            case "response.audio.delta" -> {
                var data = message.has("delta") ? message.get("delta").getAsString() : "";
                yield new OmniEvent("audio_delta", null, data, null);
            }
            case "response.audio_transcript.done" -> {
                var transcript =
                        message.has("transcript") ? message.get("transcript").getAsString() : "";
                yield new OmniEvent("transcript_done", transcript, null, null);
            }
            case "response.audio.done" -> new OmniEvent("audio_done", null, null, null);
            case "response.done" -> new OmniEvent("response_done", null, null, null);
            case "input_audio_buffer.speech_started" ->
                    new OmniEvent("speech_started", null, null, null);
            case "input_audio_buffer.speech_stopped" ->
                    new OmniEvent("speech_stopped", null, null, null);
            case "conversation.item.input_audio_transcription.completed" -> {
                var transcript =
                        message.has("transcript") ? message.get("transcript").getAsString() : "";
                yield new OmniEvent("input_transcript", transcript, null, null);
            }
            case "error" -> {
                var errMsg =
                        message.has("error")
                                ? message.get("error")
                                        .getAsJsonObject()
                                        .get("message")
                                        .getAsString()
                                : "未知错误";
                yield new OmniEvent("error", errMsg, null, null);
            }
            default -> null;
        };
    }

    /** DashScope SDK 会话句柄封装。 */
    private static class DashScopeOmniSession implements OmniSession {

        private final OmniRealtimeConversation conversation;

        DashScopeOmniSession(OmniRealtimeConversation conversation) {
            this.conversation = conversation;
        }

        @Override
        public void sendAudio(String audioBase64) {
            conversation.appendAudio(audioBase64);
        }

        @Override
        public void sendVideo(String imageBase64) {
            conversation.appendVideo(imageBase64);
        }

        @Override
        public void commit() {
            conversation.commit();
        }

        @Override
        public void createResponse() {
            conversation.createResponse(null, null);
        }

        @Override
        public void cancelResponse() {
            conversation.cancelResponse();
        }

        @Override
        public void close() {
            conversation.close();
        }

        @Override
        public String getSessionId() {
            return conversation.getSessionId();
        }
    }
}
