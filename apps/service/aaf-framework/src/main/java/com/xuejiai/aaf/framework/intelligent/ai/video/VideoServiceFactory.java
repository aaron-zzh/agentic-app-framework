package com.xuejiai.aaf.framework.intelligent.ai.video;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频生成服务工厂，根据模型类型路由到对应实现。
 *
 * <p>路由规则（按 providerType + modelName）：
 *
 * <ul>
 *   <li>DASHSCOPE + wan2.* → {@link WanxVideoGenerationService}（百炼 SDK VideoSynthesis）
 *   <li>DASHSCOPE + 其他 → {@link DashScopeVideoGenerationService}（HTTP API，happyhorse 等）
 *   <li>其他 providerType → 抛出 {@link IllegalArgumentException}
 * </ul>
 *
 * <p>任务查询统一走 {@link #getQueryService()}，因为 wan2 和 happyhorse 共用同一套 DashScope task API。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoServiceFactory {

    private final DashScopeVideoGenerationService dashScopeService;
    private final WanxVideoGenerationService wanxService;
    private final DoubaoVideoGenerationService doubaoService;

    /** 根据已 resolve 的 AiModel 获取提交服务。 */
    public VideoGenerationService getService(AiModel model) {
        var providerType = model.effectiveProviderType();
        if (providerType == AiModelProviderType.VOLCENGINE) {
            log.debug(
                    "[VideoServiceFactory] 路由到 DoubaoVideoGenerationService: model={}",
                    model.getModelName());
            return doubaoService;
        }
        if (providerType != AiModelProviderType.DASHSCOPE) {
            throw new IllegalArgumentException(
                    "模型 " + model.getModelId() + " 的协议类型 " + providerType + " 不支持视频生成");
        }
        var modelName = model.getModelName();
        if (modelName != null && modelName.startsWith("wan2.")) {
            log.debug("[VideoServiceFactory] 路由到 WanxVideoGenerationService: model={}", modelName);
            return wanxService;
        }
        log.debug("[VideoServiceFactory] 路由到 DashScopeVideoGenerationService: model={}", modelName);
        return dashScopeService;
    }

    /**
     * 获取任务查询服务。
     *
     * <p>wan2 和 happyhorse 共用同一套 DashScope task API，统一用 dashScopeService 查询。
     */
    public VideoGenerationService getQueryService() {
        return dashScopeService;
    }
}
