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

    /** 各能力的 yaml 兜底 modelId，key=capability，value=modelId */
    private final java.util.Map<String, String> fallbackModels;

    @Override
    public String resolve(CapabilityRoutingContext ctx) {
        // 1. 显式指定
        if (ctx.explicitModelId() != null) {
            log.debug(
                    "能力路由[显式]: capability={}, modelId={}", ctx.capability(), ctx.explicitModelId());
            return ctx.explicitModelId();
        }

        // 2. 编排引擎配置
        if (ctx.orchestrationModelId() != null) {
            log.debug(
                    "能力路由[编排]: capability={}, modelId={}",
                    ctx.capability(),
                    ctx.orchestrationModelId());
            return ctx.orchestrationModelId();
        }

        // 3. AI 辅助决策
        var aiSelected = aiModelSelector.select(ctx);
        if (aiSelected != null) {
            log.debug("能力路由[AI决策]: capability={}, modelId={}", ctx.capability(), aiSelected);
            return aiSelected;
        }

        // 4. 用户偏好
        if (ctx.userId() != null && ctx.capability() != null) {
            var userPref =
                    preferenceRepository
                            .findByScopeAndScopeIdAndCapability(
                                    ModelPreference.SCOPE_USER, ctx.userId(), ctx.capability())
                            .map(ModelPreference::getModelId);
            if (userPref.isPresent()) {
                log.debug(
                        "能力路由[用户偏好]: capability={}, modelId={}", ctx.capability(), userPref.get());
                return userPref.get();
            }
        }

        // 5. 系统默认
        if (ctx.capability() != null) {
            var systemDefault =
                    preferenceRepository
                            .findByScopeAndScopeIdIsNullAndCapability(
                                    ModelPreference.SCOPE_SYSTEM, ctx.capability())
                            .map(ModelPreference::getModelId);
            if (systemDefault.isPresent()) {
                log.debug(
                        "能力路由[系统默认]: capability={}, modelId={}",
                        ctx.capability(),
                        systemDefault.get());
                return systemDefault.get();
            }
        }

        // 6. yaml 兜底
        var fallback = fallbackModels.getOrDefault(ctx.capability(), "openai:gpt-4o");
        log.debug("能力路由[yaml兜底]: capability={}, modelId={}", ctx.capability(), fallback);
        return fallback;
    }
}
