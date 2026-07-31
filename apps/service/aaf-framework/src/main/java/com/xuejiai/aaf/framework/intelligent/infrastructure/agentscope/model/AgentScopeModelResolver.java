package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.model;

import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelManagementService;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;

import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;

/**
 * 从 AAF 模型真理源构建 AgentScope 2.0 Model。
 *
 * <p>模型参数（provider / baseUrl / apiKey）只来自 ai_model 表，不读环境变量或配置文件。
 */
public final class AgentScopeModelResolver {

    private final ModelManagementService models;

    public AgentScopeModelResolver(ModelManagementService models) {
        this.models = Objects.requireNonNull(models, "models 不能为空");
    }

    /** 校验模型具备 CHAT 能力后构建；密钥缺失时传空串交由 provider 报错。 */
    public Model resolve(ModelSpec spec) {
        Objects.requireNonNull(spec, "model spec 不能为空");
        var model = models.getModel(databaseId(spec));
        if (!model.hasCapability("CHAT")) {
            throw new IllegalStateException("Agent 模型不支持 CHAT: " + model.getModelId());
        }
        var apiKey = Objects.requireNonNullElse(models.resolveApiKey(model.getModelId()), "");
        return build(model, apiKey);
    }

    /** 按 provider 分派到对应扩展实现；一律开启流式，DashScope 不支持自定义 baseUrl。 */
    private Model build(AiModel model, String apiKey) {
        var providerType = model.effectiveProviderType();
        var baseUrl = model.effectiveBaseUrl();
        if (providerType == AiModelProviderType.ANTHROPIC) {
            var builder =
                    AnthropicChatModel.builder()
                            .apiKey(apiKey)
                            .modelName(model.getModelName())
                            .stream(true);
            if (hasText(baseUrl)) {
                builder.baseUrl(baseUrl);
            }
            return builder.build();
        }
        if (providerType == AiModelProviderType.DASHSCOPE) {
            return DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(model.getModelName())
                    .stream(true)
                    .build();
        }
        var builder =
                OpenAIChatModel.builder().apiKey(apiKey).modelName(model.getModelName()).stream(
                        true);
        if (hasText(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        // 其余 provider 统一走 OpenAI 兼容协议
        return builder.build();
    }

    /** ModelSpec.modelId 承载的是 ai_model 主键，非法值直接拒绝。 */
    private long databaseId(ModelSpec spec) {
        try {
            return Long.parseLong(spec.modelId());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(
                    "Agent 模型规格必须引用 ai_model 主键: " + spec.modelId(), failure);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
