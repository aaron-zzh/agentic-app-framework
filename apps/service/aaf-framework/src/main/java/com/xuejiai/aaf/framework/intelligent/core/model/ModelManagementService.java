/**
 * 模型管理服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.model;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.intelligent.ai.chat.AiProperties;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageConfig;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoConfig;

import lombok.RequiredArgsConstructor;

/** 管理 LLM 模型注册、启用/禁用、参数配置。 与 AgentScope 的 Model 集成配合使用——AgentScope 负责实际调用， 本服务负责模型元数据的持久化管理。 */
@Service
@RequiredArgsConstructor
public class ModelManagementService {

    private final AiModelRepository repository;
    private final ConfigCacheManager configCacheManager;
    private final AiProperties aiProperties;

    /** 注册新模型 */
    @Transactional
    public AiModel register(AiModel model) {
        return repository.save(model);
    }

    /** 启用模型 */
    @Transactional
    public void enable(String modelId) {
        repository
                .findByModelId(modelId)
                .ifPresent(
                        m -> {
                            m.setEnabled(true);
                            repository.save(m);
                        });
    }

    /** 禁用模型 */
    @Transactional
    public void disable(String modelId) {
        repository
                .findByModelId(modelId)
                .ifPresent(
                        m -> {
                            m.setEnabled(false);
                            repository.save(m);
                        });
    }

    /** 获取所有启用的模型 */
    public List<AiModel> listEnabled() {
        return repository.findByEnabledTrueOrderBySortOrder();
    }

    /** 按 modelId 查找（走缓存），不存在则抛异常。 */
    public AiModel getModel(String modelId) {
        var model = configCacheManager.getAiModelByModelId(modelId);
        if (model == null)
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "模型不存在: " + modelId);
        return model;
    }

    /** 按 ID 查找（走库，用于管理操作） */
    public Optional<AiModel> findByModelId(String modelId) {
        return repository.findByModelId(modelId);
    }

    /** 获取降级模型 */
    public Optional<AiModel> getFallback(String modelId) {
        return repository
                .findByModelId(modelId)
                .filter(m -> m.getFallbackModelId() != null)
                .flatMap(m -> repository.findByModelId(m.getFallbackModelId()));
    }

    /** 解析模型实际可用的 API Key：模型级 → 供应商级 → AiProperties 全局配置兜底。 */
    public String resolveApiKey(String modelId) {
        var model = configCacheManager.getAiModelByModelId(modelId);
        if (model == null) return "";
        String apiKey = model.effectiveApiKey();
        if (apiKey != null && !apiKey.isBlank()) return apiKey;
        var models = aiProperties.getModels();
        var cfg = models.getOrDefault(model.getProvider(), models.get("default"));
        return cfg != null && cfg.getApiKey() != null ? cfg.getApiKey() : "";
    }

    /** 解析模型的 ImageConfig，走缓存，解析失败返回 null。 */
    public ImageConfig resolveImageConfig(String modelId) {
        var model = configCacheManager.getAiModelByModelId(modelId);
        return model != null ? model.getImageConfigParsed() : null;
    }

    /** 解析模型的 VideoConfig，走缓存，解析失败返回 null。 */
    public VideoConfig resolveVideoConfig(String modelId) {
        var model = configCacheManager.getAiModelByModelId(modelId);
        return model != null ? model.getVideoConfigParsed() : null;
    }

    /**
     * 解析模型的 params_config 为指定类型，走缓存，解析失败返回 null。
     *
     * <p>用于 chat/ocr/speech 等能力：{@code resolveParamsConfig(modelId, ChatConfig.class)}
     */
    public <T> T resolveParamsConfig(String modelId, Class<T> type) {
        var model = configCacheManager.getAiModelByModelId(modelId);
        return model != null ? model.getParamsConfigParsed(type) : null;
    }
}
