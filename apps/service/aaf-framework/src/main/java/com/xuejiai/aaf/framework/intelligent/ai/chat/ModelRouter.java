package com.xuejiai.aaf.framework.intelligent.ai.chat;

/** 模型路由接口，根据上下文决策链解析最终 modelId。 */
public interface ModelRouter {

    /**
     * 解析 modelId。
     *
     * <p>决策链：显式指定 → 编排引擎配置 → AI 辅助决策 → 用户偏好 → 系统默认 → yaml 兜底
     *
     * @param context 路由上下文
     * @return modelId，永不为 null
     */
    String resolve(ModelRoutingContext context);
}
