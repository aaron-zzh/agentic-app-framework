package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcSlotCountOverride;

public record AigcProjectMaterializeCommand(
        Long workspaceId,
        String name,
        String description,
        String background,
        String targetAudience,
        List<String> requirements,
        List<String> constraints,
        List<String> keyFacts,
        List<String> keyMessages,
        String styleGuidance,
        List<String> successCriteria,
        Map<String, Object> deliveryRequirements,
        String locale,
        String additionalContext,
        AigcBlueprintSourceType sourceType,
        String sourceRef,
        String projectTypeCode,
        Long blueprintVersionId,
        List<Long> brandProfileVersionIds,
        List<Long> channelSpecVersionIds,
        List<AigcProjectDocumentInput> documents,
        String productionMode,
        List<AigcSlotCountOverride> slotOverrides,
        AigcProjectCoverMode coverMode,
        Long coverFileId,
        String coverPrompt,
        String coverIdempotencyKey,
        CreationProposalRef approvedCreationProposal) {

    /** 物化前草稿 Proposal 的确认凭据；省略表示本次创建没有使用 AI 辅助初始化。 */
    public record CreationProposalRef(
            String draftId, String proposalId, Integer proposalVersion, String proposalHash) {}

    public AigcProjectMaterializeCommand {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        keyFacts = keyFacts == null ? List.of() : List.copyOf(keyFacts);
        keyMessages = keyMessages == null ? List.of() : List.copyOf(keyMessages);
        successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
        deliveryRequirements =
                deliveryRequirements == null ? Map.of() : Map.copyOf(deliveryRequirements);
        brandProfileVersionIds =
                brandProfileVersionIds == null ? List.of() : List.copyOf(brandProfileVersionIds);
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
        documents = documents == null ? List.of() : List.copyOf(documents);
        slotOverrides = slotOverrides == null ? List.of() : List.copyOf(slotOverrides);
    }
}
