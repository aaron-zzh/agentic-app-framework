package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreference;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelPreferenceRepository;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

import lombok.RequiredArgsConstructor;

/** 解析并校验知识入库的系统级 Prompt 与模型配置。 */
@Component
@RequiredArgsConstructor
public class KnowledgeIngestConfigurationService {

    private final SystemConfigService systemConfigService;
    private final ModelPreferenceRepository preferenceRepository;
    private final AiModelRepository modelRepository;

    public Snapshot resolve() {
        var extractionPrompt =
                requiredPrompt(SysConfigKeys.Knowledge.EXTRACTION_SYSTEM_PROMPT, "知识事实抽取系统 Prompt");
        var entityResolutionPrompt =
                requiredPrompt(
                        SysConfigKeys.Knowledge.ENTITY_RESOLUTION_SYSTEM_PROMPT, "知识实体消歧系统 Prompt");
        return new Snapshot(
                extractionPrompt,
                TrustedKnowledgeStore.sha256(extractionPrompt),
                EntityExtractionPrompt.OUTPUT_CONTRACT_VERSION,
                resolveSystemChatModel(CapabilityRoutingContext.CAP_KNOWLEDGE_EXTRACTION),
                entityResolutionPrompt,
                TrustedKnowledgeStore.sha256(entityResolutionPrompt),
                EntityResolutionPrompt.OUTPUT_CONTRACT_VERSION,
                resolveSystemChatModel(CapabilityRoutingContext.CAP_KNOWLEDGE_ENTITY_RESOLUTION));
    }

    private String requiredPrompt(String key, String name) {
        var prompt = systemConfigService.getString(key);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalStateException(name + " 未配置");
        }
        return prompt.strip();
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

    public record Snapshot(
            String extractionSystemPrompt,
            String extractionPromptDigest,
            String extractionOutputContractVersion,
            String extractionModelId,
            String entityResolutionSystemPrompt,
            String entityResolutionPromptDigest,
            String entityResolutionOutputContractVersion,
            String entityResolutionModelId) {}
}
