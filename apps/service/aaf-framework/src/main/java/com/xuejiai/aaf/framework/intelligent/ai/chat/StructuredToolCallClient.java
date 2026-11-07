package com.xuejiai.aaf.framework.intelligent.ai.chat;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

import lombok.RequiredArgsConstructor;

/**
 * 通过强制工具调用获取结构化输出。
 *
 * <p>部分 OpenAI 兼容代理会丢弃 {@code response_format} / {@code output_config} 等原生结构化输出参数，但会透传工具调用。
 * 这里把目标类型的 JSON Schema 声明为唯一工具的入参，强制模型调用该工具，并关闭框架内部工具执行，直接读取工具入参作为结果。
 *
 * <p>OPENAI_COMPAT 通过 {@code tool_choice} 指定工具名强制调用；其他协议仅注册工具，依赖提示词要求模型调用。
 */
@Component
@RequiredArgsConstructor
public class StructuredToolCallClient {

    private final DynamicChatClientFactory chatClientFactory;
    private final AiModelRepository modelRepository;

    /**
     * 调用模型并把指定工具的入参解析为 {@code type}。
     *
     * @param type 结果类型，必须是 JSON 对象结构（OpenAI 工具入参不支持顶层数组，列表需包装为记录字段）
     */
    public <T> ResponseEntity<ChatResponse, T> call(
            String modelId,
            String systemPrompt,
            String userPrompt,
            String toolName,
            String toolDescription,
            Class<T> type) {
        var definition =
                DefaultToolDefinition.builder()
                        .name(toolName)
                        .description(toolDescription)
                        .inputSchema(JsonSchemaGenerator.generateForType(type))
                        .build();
        var response =
                chatClientFactory
                        .get(modelId)
                        .prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .options(options(modelId, new OutputToolCallback(definition)))
                        .call()
                        .chatResponse();
        return new ResponseEntity<>(
                response, JsonUtils.parseObject(toolArguments(response, toolName), type));
    }

    private ChatOptions.Builder<?> options(String modelId, ToolCallback callback) {
        var providerType =
                modelRepository
                        .findByModelIdAndEnabledTrue(modelId)
                        .orElseThrow(() -> exception(GlobalErrorCode.NOT_FOUND))
                        .effectiveProviderType();
        if (providerType == AiModelProviderType.OPENAI_COMPAT) {
            return OpenAiChatOptions.builder()
                    .toolCallbacks(callback)
                    .internalToolExecutionEnabled(false)
                    .toolChoice(
                            JsonUtils.toJsonString(
                                    java.util.Map.of(
                                            "type",
                                            "function",
                                            "function",
                                            java.util.Map.of(
                                                    "name", callback.getToolDefinition().name()))));
        }
        return ToolCallingChatOptions.builder()
                .toolCallbacks(callback)
                .internalToolExecutionEnabled(false);
    }

    private String toolArguments(ChatResponse response, String toolName) {
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("结构化工具调用返回空响应");
        }
        var output = response.getResult().getOutput();
        if (output == null || !output.hasToolCalls()) {
            throw new IllegalStateException("模型未调用结构化输出工具: " + toolName);
        }
        return output.getToolCalls().stream()
                .filter(call -> toolName.equals(call.name()))
                .map(call -> call.arguments())
                .filter(arguments -> arguments != null && !arguments.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("结构化输出工具缺少入参: " + toolName));
    }

    /** 仅用于声明工具结构；内部工具执行已关闭，不会被调用。 */
    private record OutputToolCallback(ToolDefinition definition) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            throw new IllegalStateException("结构化输出工具不应被执行: " + definition.name());
        }
    }
}
