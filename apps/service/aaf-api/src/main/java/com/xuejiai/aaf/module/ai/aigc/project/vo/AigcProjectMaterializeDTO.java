package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcSlotCountOverride;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcBlueprintSourceType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 项目物化判别联合；sourceType 决定且只允许对应分支。 */
public record AigcProjectMaterializeDTO(
        @NotNull AigcBlueprintSourceType sourceType,
        @Valid CatalogDTO catalog,
        @Valid AssistantProposalRefDTO assistantProposal) {

    public AigcProjectMaterializeDTO {
        if (sourceType == AigcBlueprintSourceType.CATALOG
                && (catalog == null || assistantProposal != null)) {
            throw new IllegalArgumentException("CATALOG 物化必须且只能提交 catalog");
        }
        if (sourceType == AigcBlueprintSourceType.ASSISTANT_GENERATED
                && (assistantProposal == null || catalog != null)) {
            throw new IllegalArgumentException("ASSISTANT_GENERATED 物化必须且只能提交 assistantProposal");
        }
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("项目物化不接受字段: " + field);
    }

    /** CATALOG 输入不再接受 sourceRef 或执行绑定版本。 */
    public record CatalogDTO(
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
            @NotBlank String projectTypeCode,
            @NotNull @Positive Long blueprintVersionId,
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

        public CatalogDTO {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            constraints = constraints == null ? List.of() : List.copyOf(constraints);
            keyFacts = keyFacts == null ? List.of() : List.copyOf(keyFacts);
            keyMessages = keyMessages == null ? List.of() : List.copyOf(keyMessages);
            successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
            deliveryRequirements =
                    deliveryRequirements == null ? Map.of() : Map.copyOf(deliveryRequirements);
            brandProfileVersionIds =
                    brandProfileVersionIds == null
                            ? List.of()
                            : List.copyOf(brandProfileVersionIds);
            channelSpecVersionIds =
                    channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
            documents = documents == null ? List.of() : List.copyOf(documents);
            slotOverrides = slotOverrides == null ? List.of() : List.copyOf(slotOverrides);
        }

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("CATALOG 物化不接受字段: " + field);
        }
    }

    /** Assistant 最终物化只提交三元凭据。 */
    public record AssistantProposalRefDTO(
            @NotBlank String proposalId,
            @NotNull @Positive Integer proposalVersion,
            @NotBlank @Size(min = 64, max = 64) String proposalHash) {

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException(
                    "ASSISTANT_GENERATED 物化只接受 proposalId/version/hash，不接受: " + field);
        }
    }

    /** CATALOG 初始化字段生成 Proposal 的凭据，与 Assistant Blueprint Proposal 严格分离。 */
    public record CreationProposalRefDTO(
            @NotBlank String draftId,
            @NotBlank String proposalId,
            @NotNull @Positive Integer proposalVersion,
            @NotBlank String proposalHash) {

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("创建初始化 Proposal 凭据不接受字段: " + field);
        }
    }
}
