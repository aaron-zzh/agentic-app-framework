package com.xuejiai.aaf.framework.engine.cache;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.prompt.PromptTemplate;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplateRepository;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinition;
import com.xuejiai.aaf.framework.intelligent.agent.AgentDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.AssistantDefinitionRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreference;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreferenceRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 配置缓存管理器——启动时预热，运行时提供快速读取。
 *
 * <p>管理 6 个缓存实例：AiModel、AgentDefinition、AssistantDefinition、
 * PromptTemplate、ModelPreference、SkillDefinition。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigCacheManager {

    private static final int MAX_SIZE = 500;
    private static final Duration LOCAL_TTL = Duration.ofMinutes(5);
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    private final TwoLevelCacheFactory cacheFactory;
    private final AiModelRepository aiModelRepository;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AssistantDefinitionRepository assistantDefinitionRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ModelPreferenceRepository modelPreferenceRepository;

    private TwoLevelCache<Long, AiModel> aiModelCache;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> aiModelIdIndex =
            new java.util.concurrent.ConcurrentHashMap<>();
    private TwoLevelCache<Long, AgentDefinition> agentDefCache;
    private TwoLevelCache<Long, AssistantDefinition> assistantDefCache;
    private TwoLevelCache<Long, PromptTemplate> promptTemplateCache;
    private TwoLevelCache<Long, ModelPreference> modelPreferenceCache;
    private TwoLevelCache<Long, SkillDefinition> skillDefCache;

    @PostConstruct
    void init() {
        aiModelCache =
                cacheFactory.create("ai_model", AiModel.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        agentDefCache =
                cacheFactory.create(
                        "agent_def", AgentDefinition.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        assistantDefCache =
                cacheFactory.create(
                        "assistant_def", AssistantDefinition.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        promptTemplateCache =
                cacheFactory.create(
                        "prompt_tpl", PromptTemplate.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        modelPreferenceCache =
                cacheFactory.create(
                        "model_pref", ModelPreference.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        skillDefCache =
                cacheFactory.create(
                        "skill_def", SkillDefinition.class, MAX_SIZE, LOCAL_TTL, REDIS_TTL);
        warmUp();
    }

    public AiModel getAiModel(Long id) {
        return aiModelCache.get(id, k -> aiModelRepository.findById(k).orElse(null));
    }

    /** 按 modelId 字符串查询（如 "n1n:gpt-4o"），先走 id 索引再查缓存 */
    public AiModel getAiModelByModelId(String modelId) {
        if (modelId == null) return null;
        Long id =
                aiModelIdIndex.computeIfAbsent(
                        modelId,
                        k -> aiModelRepository.findByModelId(k).map(AiModel::getId).orElse(null));
        if (id == null) return null;
        AiModel model = getAiModel(id);
        // 校验一致性：DB 记录的 modelId 可能已变更，index 中的映射已过时
        if (model != null && !modelId.equals(model.getModelId())) {
            log.warn(
                    "[ConfigCache] aiModelIdIndex 过时: key={} → id={} 实际modelId={}, 重建索引",
                    modelId,
                    id,
                    model.getModelId());
            aiModelIdIndex.remove(modelId);
            Long freshId =
                    aiModelRepository.findByModelId(modelId).map(AiModel::getId).orElse(null);
            if (freshId == null) return null;
            aiModelIdIndex.put(modelId, freshId);
            return getAiModel(freshId);
        }
        return model;
    }

    public AgentDefinition getAgentDef(Long id) {
        return agentDefCache.get(id, k -> agentDefinitionRepository.findById(k).orElse(null));
    }

    public AssistantDefinition getAssistantDef(Long id) {
        return assistantDefCache.get(
                id, k -> assistantDefinitionRepository.findById(k).orElse(null));
    }

    public PromptTemplate getPromptTemplate(Long id) {
        return promptTemplateCache.get(id, k -> promptTemplateRepository.findById(k).orElse(null));
    }

    public ModelPreference getModelPreference(Long id) {
        return modelPreferenceCache.get(
                id, k -> modelPreferenceRepository.findById(k).orElse(null));
    }

    public SkillDefinition getSkillDef(Long id) {
        return skillDefCache.get(id, k -> null); // SkillDefinition 暂无 Repository，按需扩展
    }

    private void warmUp() {
        log.info("开始预热配置缓存...");
        aiModelRepository
                .findAll()
                .forEach(
                        m -> {
                            aiModelCache.put(m.getId(), m);
                            if (m.getModelId() != null)
                                aiModelIdIndex.put(m.getModelId(), m.getId());
                        });
        agentDefinitionRepository.findAll().forEach(a -> agentDefCache.put(a.getId(), a));
        assistantDefinitionRepository.findAll().forEach(a -> assistantDefCache.put(a.getId(), a));
        promptTemplateRepository.findAll().forEach(p -> promptTemplateCache.put(p.getId(), p));
        modelPreferenceRepository.findAll().forEach(p -> modelPreferenceCache.put(p.getId(), p));
        log.info("配置缓存预热完成");
    }
}
