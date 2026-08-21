package com.xuejiai.aaf.framework.engine.cache;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreference;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreferenceRepository;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 配置缓存管理器：只缓存模型与模型偏好；Prompt 由版本化 PromptEngine 解析。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigCacheManager {

    private static final int MAX_SIZE = 500;
    private static final Duration LOCAL_TTL = Duration.ofMinutes(5);
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    private final TwoLevelCacheFactory cacheFactory;
    private final AiModelRepository aiModelRepository;
    private final ModelPreferenceRepository modelPreferenceRepository;

    private TwoLevelCache<Long, AiModel> aiModelCache;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> aiModelIdIndex =
            new java.util.concurrent.ConcurrentHashMap<>();
    private TwoLevelCache<Long, ModelPreference> modelPreferenceCache;

    @PostConstruct
    void init() {
        aiModelCache =
                cacheFactory.create("ai_model", AiModel.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        modelPreferenceCache =
                cacheFactory.create(
                        "model_pref", ModelPreference.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        OrgContext.runIgnoring(this::warmUp);
    }

    public AiModel getAiModel(Long id) {
        return aiModelCache.get(id, key -> aiModelRepository.findById(key).orElse(null));
    }

    /** 按 modelId 查询，索引失效时回查数据库并重建。 */
    public AiModel getAiModelByModelId(String modelId) {
        if (modelId == null) return null;
        var id =
                aiModelIdIndex.computeIfAbsent(
                        modelId,
                        key ->
                                aiModelRepository
                                        .findByModelId(key)
                                        .map(AiModel::getId)
                                        .orElse(null));
        if (id == null) return null;
        var model = getAiModel(id);
        if (model != null && !modelId.equals(model.getModelId())) {
            aiModelIdIndex.remove(modelId);
            var freshId = aiModelRepository.findByModelId(modelId).map(AiModel::getId).orElse(null);
            if (freshId == null) return null;
            aiModelIdIndex.put(modelId, freshId);
            return getAiModel(freshId);
        }
        return model;
    }

    @OrgIgnore
    public ModelPreference getModelPreference(Long id) {
        return modelPreferenceCache.get(
                id, key -> modelPreferenceRepository.findById(key).orElse(null));
    }

    private void warmUp() {
        log.info("开始预热配置缓存...");
        aiModelRepository
                .findAll()
                .forEach(
                        model -> {
                            aiModelCache.put(model.getId(), model);
                            if (model.getModelId() != null)
                                aiModelIdIndex.put(model.getModelId(), model.getId());
                        });
        modelPreferenceRepository
                .findAll()
                .forEach(preference -> modelPreferenceCache.put(preference.getId(), preference));
        log.info("配置缓存预热完成");
    }
}
