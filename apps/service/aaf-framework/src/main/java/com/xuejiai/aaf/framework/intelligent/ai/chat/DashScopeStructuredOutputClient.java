package com.xuejiai.aaf.framework.intelligent.ai.chat;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Component;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.ResponseFormat;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import com.google.gson.JsonParser;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通过百炼 DashScope SDK 的 JSON Schema 结构化输出获取结构化结果。
 *
 * <p>服务端按 schema 约束生成（{@code strict=true}），输出必为合法 JSON 且符合结构，避免强制工具调用时模型写出未转义引号、
 * 导致入参被降级为字符串而整体解析失败。仅适用于百炼千问模型。
 *
 * <p>连接参数沿用模型配置（模型级 > 供应商级 > yaml {@code aaf.ai.models.{provider}}）；模型登记的是 OpenAI 兼容地址 {@code
 * .../compatible-mode/v1}，原生接口地址按同一域名换算为 {@code .../api/v1}。
 *
 * <p>返回值与 {@link StructuredToolCallClient} 一致：结构化结果连同含用量的 {@link ChatResponse}，供计量复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeStructuredOutputClient {

    private static final String COMPATIBLE_MODE_PATH = "/compatible-mode/v1";
    private static final String NATIVE_API_PATH = "/api/v1";

    private final AiModelRepository modelRepository;
    private final AiProperties aiProperties;

    /**
     * 调用模型并把按 schema 约束生成的 JSON 解析为 {@code type}。
     *
     * @param schemaName JSON Schema 名称
     * @param schemaJson 顶层为对象的 JSON Schema 文本
     */
    public <T> ResponseEntity<ChatResponse, T> call(
            String modelId,
            String systemPrompt,
            String userPrompt,
            String schemaName,
            String schemaJson,
            Class<T> type) {
        var model =
                modelRepository
                        .findByModelIdAndEnabledTrue(modelId)
                        .orElseThrow(() -> exception(GlobalErrorCode.NOT_FOUND));
        var connection = resolveConnection(model);
        var param =
                MultiModalConversationParam.builder()
                        .apiKey(connection.apiKey())
                        .model(model.getModelName())
                        .messages(
                                List.of(
                                        message(Role.SYSTEM, systemPrompt),
                                        message(Role.USER, userPrompt)))
                        .responseFormat(responseFormat(schemaName, schemaJson))
                        // 结构化抽取不需要推理过程，关闭思考以降低耗时与输出 Token
                        .enableThinking(false)
                        .temperature(
                                model.getTemperature() == null
                                        ? null
                                        : model.getTemperature().floatValue())
                        .maxTokens(model.getMaxTokens())
                        .build();
        var result = invoke(connection.nativeBaseUrl(), param);
        var text = outputText(result);
        return new ResponseEntity<>(
                chatResponse(model, result, text), JsonUtils.parseObject(text, type));
    }

    private MultiModalConversationResult invoke(
            String nativeBaseUrl, MultiModalConversationParam param) {
        try {
            return new MultiModalConversation(Protocol.HTTP.getValue(), nativeBaseUrl).call(param);
        } catch (ApiException failure) {
            if (isThrottled(failure)) {
                throw new ModelThrottledException("百炼结构化输出被限频: " + failure.getMessage(), failure);
            }
            throw new IllegalStateException("百炼结构化输出调用失败: " + failure.getMessage(), failure);
        } catch (NoApiKeyException | UploadFileException failure) {
            throw new IllegalStateException("百炼结构化输出调用失败: " + failure.getMessage(), failure);
        }
    }

    /** 百炼限频返回 HTTP 429，错误码以 Throttling 开头（如 Throttling.RateQuota）；此时请求未被执行。 */
    private static boolean isThrottled(ApiException failure) {
        var status = failure.getStatus();
        if (status == null) {
            return false;
        }
        return status.getStatusCode() == 429
                || (status.getCode() != null && status.getCode().startsWith("Throttling"));
    }

    private static MultiModalMessage message(Role role, String text) {
        return MultiModalMessage.builder()
                .role(role.getValue())
                .content(List.of(Map.of("text", Objects.requireNonNull(text, "text 不能为空"))))
                .build();
    }

    private static ResponseFormat responseFormat(String schemaName, String schemaJson) {
        return ResponseFormat.builder()
                .type(ResponseFormat.JSON_SCHEMA)
                .jsonSchema(
                        ResponseFormat.JsonSchemaFormat.builder()
                                .name(schemaName)
                                .strict(true)
                                .schema(JsonParser.parseString(schemaJson).getAsJsonObject())
                                .build())
                .build();
    }

    private static String outputText(MultiModalConversationResult result) {
        if (result == null
                || result.getOutput() == null
                || result.getOutput().getChoices() == null
                || result.getOutput().getChoices().isEmpty()) {
            throw new IllegalStateException("百炼结构化输出返回空响应");
        }
        var message = result.getOutput().getChoices().getFirst().getMessage();
        var content = message == null ? null : message.getContent();
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("百炼结构化输出缺少正文");
        }
        if (!(content.getFirst().get("text") instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("百炼结构化输出正文为空");
        }
        return text;
    }

    private static ChatResponse chatResponse(
            AiModel model, MultiModalConversationResult result, String text) {
        var usage = result.getUsage();
        var metadata =
                ChatResponseMetadata.builder()
                        .id(result.getRequestId())
                        .model(model.getModelName())
                        .usage(
                                new DefaultUsage(
                                        usage == null ? null : usage.getInputTokens(),
                                        usage == null ? null : usage.getOutputTokens()))
                        .build();
        return new ChatResponse(
                List.of(new Generation(AssistantMessage.builder().content(text).build())),
                metadata);
    }

    /** apiKey/baseUrl 优先级：模型级 > 供应商级 > yaml aaf.ai.models.{provider} > default。 */
    private Connection resolveConnection(AiModel model) {
        var apiKey = model.effectiveApiKey();
        var baseUrl = model.effectiveBaseUrl();
        if (isBlank(apiKey) || isBlank(baseUrl)) {
            var models = aiProperties.getModels();
            var fallback = models.getOrDefault(model.getProvider(), models.get("default"));
            if (fallback != null) {
                if (isBlank(apiKey)) {
                    apiKey = fallback.getApiKey();
                }
                if (isBlank(baseUrl)) {
                    baseUrl = fallback.getBaseUrl();
                }
            }
        }
        if (isBlank(apiKey) || isBlank(baseUrl)) {
            throw new IllegalStateException("百炼结构化输出缺少连接配置: modelId=" + model.getModelId());
        }
        return new Connection(apiKey, nativeBaseUrl(model.getModelId(), baseUrl));
    }

    /** 由模型登记的 OpenAI 兼容地址换算百炼原生接口地址；无法识别时直接失败，不猜测。 */
    static String nativeBaseUrl(String modelId, String baseUrl) {
        var normalized = baseUrl.strip().replaceAll("/+$", "");
        if (normalized.endsWith(COMPATIBLE_MODE_PATH)) {
            return normalized.substring(0, normalized.length() - COMPATIBLE_MODE_PATH.length())
                    + NATIVE_API_PATH;
        }
        if (normalized.endsWith(NATIVE_API_PATH)) {
            return normalized;
        }
        throw new IllegalStateException(
                "模型地址不是百炼接口，不能使用百炼结构化输出: modelId=%s, baseUrl=%s".formatted(modelId, baseUrl));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Connection(String apiKey, String nativeBaseUrl) {}
}
