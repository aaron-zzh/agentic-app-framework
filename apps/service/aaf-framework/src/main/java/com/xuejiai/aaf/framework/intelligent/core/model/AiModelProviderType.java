package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 模型协议类型，决定运行时 SDK 选择。
 *
 * <p>对应字典类型：{@code ai_model_provider_type}
 */
@Getter
@AllArgsConstructor
public enum AiModelProviderType implements ArrayValuable<String> {
    OPENAI_COMPAT("OPENAI_COMPAT", "OpenAI 兼容（images/generations）"),
    ANTHROPIC("ANTHROPIC", "Anthropic"),
    OLLAMA("OLLAMA", "Ollama"),
    DASHSCOPE("DASHSCOPE", "阿里云百炼"),
    VOLCENGINE("VOLCENGINE", "火山引擎方舟");

    private final String code;
    private final String description;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AiModelProviderType::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
