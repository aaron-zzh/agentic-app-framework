package com.xuejiai.aaf.framework.intelligent.ai.realtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 实时多模态对话配置。 */
public record RealtimeConversationConfig(
        /** 音色 */
        String voice,
        /** 是否开启输入音频转录 */
        boolean enableInputAudioTranscription,
        /** 系统指令 */
        String instructions,
        /** 输出模态 */
        List<Modality> modalities,
        /** 语音活动检测；null 表示手动提交 */
        TurnDetection turnDetection,
        /** 生成参数 */
        Generation generation,
        /** 是否启用联网搜索 */
        boolean enableSearch,
        /** 联网搜索参数 */
        Map<String, Object> searchOptions,
        /** 调用方负责执行的函数工具 */
        List<FunctionTool> tools) {

    public RealtimeConversationConfig {
        if (voice != null && voice.isBlank()) {
            throw new IllegalArgumentException("voice 不能为空白");
        }
        modalities = List.copyOf(Objects.requireNonNull(modalities, "modalities 不能为空"));
        if (!modalities.equals(List.of(Modality.TEXT))
                && !modalities.equals(List.of(Modality.TEXT, Modality.AUDIO))) {
            throw new IllegalArgumentException("modalities 仅允许 [text] 或 [text,audio]");
        }
        generation = Objects.requireNonNull(generation, "generation 不能为空");
        searchOptions = immutableMap(searchOptions);
        tools = List.copyOf(tools == null ? List.of() : tools);
        validateUniqueToolNames(tools);
        if (!enableSearch && !searchOptions.isEmpty()) {
            throw new IllegalArgumentException("enableSearch=false 时不能设置 searchOptions");
        }
        if (enableSearch && !tools.isEmpty()) {
            throw new IllegalArgumentException("tools 与 enableSearch 不能同时启用");
        }
    }

    /** 创建使用官方默认生成参数的音频对话配置。 */
    public static RealtimeConversationConfig audioDefaults(
            String voice, String instructions, TurnDetection turnDetection) {
        return audio(voice, instructions, turnDetection, Generation.defaults(), List.of());
    }

    /** 创建允许调用方工具的音频对话配置。 */
    public static RealtimeConversationConfig audio(
            String voice,
            String instructions,
            TurnDetection turnDetection,
            Generation generation,
            List<FunctionTool> tools) {
        return new RealtimeConversationConfig(
                voice,
                true,
                instructions,
                List.of(Modality.TEXT, Modality.AUDIO),
                turnDetection,
                generation,
                false,
                Map.of(),
                tools);
    }

    /** 输出模态。 */
    public enum Modality {
        TEXT,
        AUDIO
    }

    /** 语音活动检测类型。 */
    public enum TurnDetectionType {
        SERVER_VAD("server_vad"),
        SEMANTIC_VAD("semantic_vad");

        private final String value;

        TurnDetectionType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static TurnDetectionType fromValue(String value) {
            for (var type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("vadType 仅允许 server_vad 或 semantic_vad");
        }
    }

    /** 语音活动检测参数；null 参数由模型使用官方默认值。 */
    public record TurnDetection(
            TurnDetectionType type,
            Float threshold,
            Integer prefixPaddingMs,
            Integer silenceDurationMs,
            Integer idleTimeoutMs) {

        public TurnDetection {
            Objects.requireNonNull(type, "turnDetection.type 不能为空");
            if (threshold != null
                    && (!Float.isFinite(threshold) || threshold < -1 || threshold > 1)) {
                throw new IllegalArgumentException("vadThreshold 必须在 [-1,1] 范围内");
            }
            if (prefixPaddingMs != null && prefixPaddingMs < 0) {
                throw new IllegalArgumentException("prefixPaddingMs 不能小于 0");
            }
            if (silenceDurationMs != null
                    && (silenceDurationMs < 200 || silenceDurationMs > 6000)) {
                throw new IllegalArgumentException("vadSilenceMs 必须在 [200,6000] 范围内");
            }
            if (idleTimeoutMs != null) {
                if (type != TurnDetectionType.SERVER_VAD) {
                    throw new IllegalArgumentException("vadIdleTimeoutMs 仅适用于 server_vad");
                }
                if (idleTimeoutMs < 5000 || idleTimeoutMs > 30000) {
                    throw new IllegalArgumentException("vadIdleTimeoutMs 必须在 [5000,30000] 范围内");
                }
            }
        }

        public static TurnDetection serverVad() {
            return new TurnDetection(TurnDetectionType.SERVER_VAD, null, null, null, null);
        }
    }

    /** 模型生成参数；null 字段由模型使用官方默认值。 */
    public record Generation(
            Double temperature,
            Double topP,
            Integer topK,
            Integer maxTokens,
            Double repetitionPenalty,
            Double presencePenalty,
            Integer seed) {

        public Generation {
            if (temperature != null
                    && (!Double.isFinite(temperature) || temperature < 0 || temperature >= 2)) {
                throw new IllegalArgumentException("temperature 必须在 [0,2) 范围内");
            }
            if (topP != null && (!Double.isFinite(topP) || topP <= 0 || topP > 1)) {
                throw new IllegalArgumentException("topP 必须在 (0,1] 范围内");
            }
            if (topK != null && topK < 0) {
                throw new IllegalArgumentException("topK 不能小于 0");
            }
            if (maxTokens != null && maxTokens <= 0) {
                throw new IllegalArgumentException("maxTokens 必须大于 0");
            }
            if (repetitionPenalty != null
                    && (!Double.isFinite(repetitionPenalty) || repetitionPenalty <= 0)) {
                throw new IllegalArgumentException("repetitionPenalty 必须大于 0");
            }
            if (presencePenalty != null
                    && (!Double.isFinite(presencePenalty)
                            || presencePenalty < -2
                            || presencePenalty > 2)) {
                throw new IllegalArgumentException("presencePenalty 必须在 [-2,2] 范围内");
            }
            if (seed != null && seed < 0) {
                throw new IllegalArgumentException("seed 必须在 [0,2147483647] 范围内");
            }
        }

        public static Generation defaults() {
            return new Generation(null, null, null, null, null, null, null);
        }
    }

    /** Function Calling 工具声明。 */
    public record FunctionTool(String name, String description, Map<String, Object> parameters) {

        public FunctionTool {
            if (name == null || !name.matches("[A-Za-z0-9_.-]{1,64}")) {
                throw new IllegalArgumentException("工具 name 必须为 1 到 64 位字母、数字、下划线、点或连字符");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("工具 description 不能为空");
            }
            parameters = immutableMap(parameters);
            if (!"object".equals(parameters.get("type"))) {
                throw new IllegalArgumentException("工具 parameters 顶层 type 必须为 object");
            }
        }
    }

    private static void validateUniqueToolNames(List<FunctionTool> tools) {
        var names = new LinkedHashSet<String>();
        for (var tool : tools) {
            Objects.requireNonNull(tool, "tools 不能包含 null");
            if (!names.add(tool.name())) {
                throw new IllegalArgumentException("工具 name 不能重复: " + tool.name());
            }
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        source.forEach(
                (key, value) -> {
                    if (key == null) {
                        throw new IllegalArgumentException("Map key 不能为 null");
                    }
                    copy.put(key, immutableValue(value));
                });
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach(
                    (key, nestedValue) -> {
                        if (!(key instanceof String stringKey)) {
                            throw new IllegalArgumentException("Map key 必须为 String");
                        }
                        copy.put(stringKey, immutableValue(nestedValue));
                    });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<>();
            list.forEach(item -> copy.add(immutableValue(item)));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
