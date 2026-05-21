package com.xuejiai.aaf.framework.intelligent.ai.chat;

import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreference;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreferenceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认模型路由实现，按六层决策链解析 modelId。
 *
 * <pre>
 * 1. 显式指定      调用方直接传 explicitModelId
 * 2. 编排引擎配置  工作流节点 / AgentDefinition 绑定的 orchestrationModelId
 * 3. AI 辅助决策   AiModelSelector 根据任务特征选模型
 * 4. 用户偏好      用户为该能力设置的默认模型
 * 5. 系统默认      管理员配置的全局默认
 * 6. yaml 兜底    AiProperties.defaultModel
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultModelRouter implements ModelRouter {

    private final AiProperties properties;
    private final ModelPreferenceRepository preferenceRepository;
    private final AiModelSelector aiModelSelector;

    @Override
    public String resolve(ModelRoutingContext ctx) {
        // 1. 显式指定
        if (ctx.explicitModelId() != null) {
            log.debug("模型路由[显式]: {}", ctx.explicitModelId());
            return ctx.explicitModelId();
        }

        // 2. 编排引擎配置
        if (ctx.orchestrationModelId() != null) {
            log.debug("模型路由[编排]: {}", ctx.orchestrationModelId());
            return ctx.orchestrationModelId();
        }

        // 3. AI 辅助决策
        var aiSelected = aiModelSelector.select(ctx);
        if (aiSelected != null) {
            log.debug("模型路由[AI决策]: {}", aiSelected);
            return aiSelected;
        }

        // 4. 用户偏好
        if (ctx.userId() != null && ctx.capability() != null) {
            var userPref = preferenceRepository
                    .findByScopeAndScopeIdAndCapability(
                            ModelPreference.SCOPE_USER, ctx.userId(), ctx.capability())
                    .map(ModelPreference::getModelId);
            if (userPref.isPresent()) {
                log.debug("模型路由[用户偏好]: {}", userPref.get());
                return userPref.get();
            }
        }

        // 5. 系统默认
        if (ctx.capability() != null) {
            var systemDefault = preferenceRepository
                    .findByScopeAndScopeIdIsNullAndCapability(
                            ModelPreference.SCOPE_SYSTEM, ctx.capability())
                    .map(ModelPreference::getModelId);
            if (systemDefault.isPresent()) {
                log.debug("模型路由[系统默认]: {}", systemDefault.get());
                return systemDefault.get();
            }
        }

        // 6. yaml 兜底
        var fallback = properties.getDefaultModel();
        log.debug("模型路由[yaml兜底]: {}", fallback);
        return fallback != null ? fallback : "openai:gpt-4o";
    }
}
