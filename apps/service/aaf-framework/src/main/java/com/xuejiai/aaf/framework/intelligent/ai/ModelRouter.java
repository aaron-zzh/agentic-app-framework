/**
 * 模型路由接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.ai;

/** 根据场景解析对应的模型配置。 */
public interface ModelRouter {

    /**
     * 根据场景名解析模型配置。
     *
     * @param scene 场景名（对应 AiProperties.models 的 key）
     * @return 模型配置，找不到则返回默认模型
     */
    AiProperties.ModelConfig resolve(String scene);
}
