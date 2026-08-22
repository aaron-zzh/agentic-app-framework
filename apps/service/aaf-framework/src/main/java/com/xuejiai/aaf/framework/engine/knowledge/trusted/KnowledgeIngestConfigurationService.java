package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreference;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreferenceRepository;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptTemplateService;
import com.xuejiai.aaf.framework.intelligent.core.prompt.ResolvedPromptTemplate;

import lombok.RequiredArgsConstructor;

/** 解析知识入库的已发布 PROCESSING Prompt 与系统模型偏好。 */
@Component
@RequiredArgsConstructor
public class KnowledgeIngestConfigurationService {

    public static final String EXTRACTION_SYSTEM_PROMPT = "aaf.knowledge.fact-extraction.system";
    public static final String EXTRACTION_USER_PROMPT = "aaf.knowledge.fact-extraction.user";
    public static final String RESOLUTION_SYSTEM_PROMPT = "aaf.knowledge.entity-resolution.system";
    public static final String RESOLUTION_USER_PROMPT = "aaf.knowledge.entity-resolution.user";

    private final PromptTemplateService promptTemplates;
    private final ModelPreferenceRepository preferenceRepository;
    private final AiModelRepository modelRepository;

    public Snapshot resolve() {
        var extractionSystem = promptTemplates.requirePublished(EXTRACTION_SYSTEM_PROMPT);
        var extractionUser = promptTemplates.requirePublished(EXTRACTION_USER_PROMPT);
        var resolutionSystem = promptTemplates.requirePublished(RESOLUTION_SYSTEM_PROMPT);
        var resolutionUser = promptTemplates.requirePublished(RESOLUTION_USER_PROMPT);
        return new Snapshot(
                extractionSystem,
                extractionUser,
                EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION,
                resolveSystemChatModel(CapabilityRoutingContext.CAP_KNOWLEDGE_EXTRACTION),
                resolutionSystem,
                resolutionUser,
                EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION,
                resolveSystemChatModel(CapabilityRoutingContext.CAP_KNOWLEDGE_ENTITY_RESOLUTION));
    }

    private String resolveSystemChatModel(String capability) {
        var preference =
                preferenceRepository
                        .findByScopeAndScopeIdIsNullAndCapability(
                                ModelPreference.SCOPE_SYSTEM, capability)
                        .orElseThrow(() -> new IllegalStateException("缺少系统模型偏好: " + capability));
        if (preference.getModelIds() == null || preference.getModelIds().isEmpty()) {
            throw new IllegalStateException("系统模型偏好没有候选模型: " + capability);
        }
        return preference.getModelIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(modelId -> !modelId.isEmpty())
                .map(modelRepository::findByModelIdAndEnabledTrue)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(model -> model.hasCapability(CapabilityRoutingContext.CAP_CHAT))
                .map(model -> model.getModelId())
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("系统模型偏好没有已启用的 CHAT 模型: " + capability));
    }

    /**
     * 入库配置快照。
     *
     * <p>指纹使用的摘要必须同时覆盖 system 与 user 两个模板——只取其一时，改动另一个不会让指纹失效，会导致复用陈旧的入库结果。
     */
    public record Snapshot(
            ResolvedPromptTemplate extractionSystem,
            ResolvedPromptTemplate extractionUser,
            String extractionOutputContractVersion,
            String extractionModelId,
            ResolvedPromptTemplate entityResolutionSystem,
            ResolvedPromptTemplate entityResolutionUser,
            String entityResolutionOutputContractVersion,
            String entityResolutionModelId) {

        /** 事实抽取 system + user 模板对的合并摘要。 */
        public String extractionPromptPairDigest() {
            return pairDigest(extractionSystem, extractionUser);
        }

        /** 实体归一 system + user 模板对的合并摘要。 */
        public String entityResolutionPromptPairDigest() {
            return pairDigest(entityResolutionSystem, entityResolutionUser);
        }

        private static String pairDigest(
                ResolvedPromptTemplate system, ResolvedPromptTemplate user) {
            return TrustedKnowledgeStore.sha256(system.sha256() + "|" + user.sha256());
        }
    }
}
