package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;
import java.util.Map;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcSlotCountOverride;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcBlueprintSourceType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AigcProjectMaterializeDTO(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String description,
        String background,
        String targetAudience,
        List<String> requirements,
        List<String> constraints,
        List<String> keyFacts,
        List<String> keyMessages,
        String styleGuidance,
        List<String> successCriteria,
        Map<String, Object> deliveryRequirements,
        @Size(max = 32) String locale,
        String additionalContext,
        @NotNull AigcBlueprintSourceType sourceType,
        @NotBlank String sourceRef,
        @NotBlank String projectTypeCode,
        @NotNull Long blueprintVersionId,
        List<Long> brandProfileVersionIds,
        List<Long> channelSpecVersionIds,
        List<AigcProjectDocumentInputDTO> documents,
        @NotBlank String productionMode,
        List<AigcSlotCountOverride> slotOverrides,
        @NotNull AigcProjectCoverMode coverMode,
        Long coverFileId,
        String coverPrompt,
        String coverIdempotencyKey,
        @Valid CreationProposalRefDTO approvedCreationProposal) {

    /** 可选：本次创建使用的已确认物化前草稿 Proposal。 */
    public record CreationProposalRefDTO(
            @NotBlank String draftId,
            @NotBlank String proposalId,
            @NotNull Integer proposalVersion,
            @NotBlank String proposalHash) {}

    public AigcProjectMaterializeDTO {
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
