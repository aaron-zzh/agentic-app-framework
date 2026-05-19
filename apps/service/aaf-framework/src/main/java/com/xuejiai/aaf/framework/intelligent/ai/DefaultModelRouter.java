/**
 * 默认模型路由实现。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai;

import lombok.RequiredArgsConstructor;

/** 从配置中按场景查找模型，找不到则返回默认模型配置。 */
@RequiredArgsConstructor
public class DefaultModelRouter implements ModelRouter {

    private final AiProperties properties;

    @Override
    public AiProperties.ModelConfig resolve(String scene) {
        var models = properties.getModels();
        if (scene != null && models.containsKey(scene)) {
            return models.get(scene);
        }
        // 返回默认模型配置
        var defaultKey = properties.getDefaultModel();
        if (defaultKey != null && models.containsKey(defaultKey)) {
            return models.get(defaultKey);
        }
        // 兜底：返回空配置（使用 Spring AI 自动配置的默认模型）
        return new AiProperties.ModelConfig();
    }
}
