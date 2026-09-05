package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectCoverMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AigcProjectMaterializeDTO(
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotBlank String projectTypeCode,
        @NotNull Long blueprintVersionId,
        Long domainExtensionVersionId,
        List<Long> brandProfileVersionIds,
        List<Long> channelSpecVersionIds,
        List<Long> documentVersionIds,
        @NotBlank String productionMode,
        String budgetTier,
        String qualityTier,
        List<com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcSlotCountOverride> slotOverrides,
        String briefJson,
        @NotNull AigcProjectCoverMode coverMode,
        Long coverFileId,
        String coverPrompt,
        String coverIdempotencyKey) {

    public AigcProjectMaterializeDTO {
        brandProfileVersionIds =
                brandProfileVersionIds == null ? List.of() : List.copyOf(brandProfileVersionIds);
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
        documentVersionIds =
                documentVersionIds == null ? List.of() : List.copyOf(documentVersionIds);
        slotOverrides = slotOverrides == null ? List.of() : List.copyOf(slotOverrides);
    }
}
