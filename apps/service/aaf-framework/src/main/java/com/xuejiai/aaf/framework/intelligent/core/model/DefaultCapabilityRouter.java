package com.xuejiai.aaf.framework.intelligent.core.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认能力路由实现，按六层决策链解析 modelId。
 *
 * <pre>
 * 1. 显式指定      调用方直接传 explicitModelId
 * 2. 编排引擎配置  工作流节点 / AgentDefinition 绑定的 orchestrationModelId
 * 3. AI 辅助决策   AiModelSelector 根据任务特征选模型
 * 4. 用户偏好      用户为该能力设置的默认模型（DB：ai_model_preference，scope=USER）
 * 5. 系统默认      管理员配置的全局默认（DB：ai_model_preference，scope=SYSTEM）
 * 6. yaml 兜底    能力对应的默认 modelId
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultCapabilityRouter implements CapabilityRouter {

    private final ModelPreferenceRepository preferenceRepository;
    private final AiModelSelector aiModelSelector;
    private final AiModelRepository modelRepository;

    /** 各能力的 yaml 兜底 modelId，key=capability，value=modelId */
    private final java.util.Map<String, String> fallbackModels;

    @Override
    public AiModel resolve(CapabilityRoutingContext ctx) {
        // 1. 显式指定
        if (ctx.explicitModelId() != null) {
            log.debug(
                    "能力路由[显式]: capability={}, modelId={}", ctx.capability(), ctx.explicitModelId());
            return loadByModelId(ctx.explicitModelId());
        }

        // 2. 编排引擎配置
        if (ctx.orchestrationModelId() != null) {
            log.debug(
                    "能力路由[编排]: capability={}, modelId={}",
                    ctx.capability(),
                    ctx.orchestrationModelId());
            return loadByModelId(ctx.orchestrationModelId());
        }

        // 3. AI 辅助决策
        var aiSelected = aiModelSelector.select(ctx);
        if (aiSelected != null) {
            log.debug(
                    "能力路由[AI决策]: capability={}, modelId={}",
                    ctx.capability(),
                    aiSelected.getModelId());
            return aiSelected;
        }

        // 4. 用户偏好（按顺序取第一个 enabled 的模型）
        if (ctx.userId() != null && ctx.capability() != null) {
            var userModel =
                    preferenceRepository
                            .findByScopeAndScopeIdAndCapability(
                                    ModelPreference.SCOPE_USER, ctx.userId(), ctx.capability())
                            .flatMap(pref -> resolveFirstAvailable(pref.getModelIds()));
            if (userModel.isPresent()) {
                log.debug(
                        "能力路由[用户偏好]: capability={}, modelId={}",
                        ctx.capability(),
                        userModel.get().getModelId());
                return userModel.get();
            }
        }

        // 5. 系统默认（按顺序取第一个 enabled 的模型）
        if (ctx.capability() != null) {
            var systemModel =
                    preferenceRepository
                            .findByScopeAndScopeIdIsNullAndCapability(
                                    ModelPreference.SCOPE_SYSTEM, ctx.capability())
                            .flatMap(pref -> resolveFirstAvailable(pref.getModelIds()));
            if (systemModel.isPresent()) {
                log.debug(
                        "能力路由[系统默认]: capability={}, modelId={}",
                        ctx.capability(),
                        systemModel.get().getModelId());
                return systemModel.get();
            }
        }

        // 6. yaml 兜底
        var fallbackId = fallbackModels.getOrDefault(ctx.capability(), "openai:gpt-4o");
        log.debug("能力路由[yaml兜底]: capability={}, modelId={}", ctx.capability(), fallbackId);
        return loadByModelId(fallbackId);
    }

    private AiModel loadByModelId(String modelId) {
        return modelRepository
                .findByModelId(modelId)
                .orElseThrow(() -> new IllegalStateException("模型不存在: " + modelId));
    }

    /** 按顺序取 modelIds 中第一个 enabled=true 的模型 */
    private java.util.Optional<AiModel> resolveFirstAvailable(java.util.List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) return java.util.Optional.empty();
        return modelIds.stream()
                .map(id -> modelRepository.findByModelIdAndEnabledTrue(id))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst();
    }
}
