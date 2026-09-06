package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcSlotCountOverride;

public record AigcProjectMaterializeCommand(
        Long workspaceId,
        String name,
        String description,
        String projectTypeCode,
        Long blueprintVersionId,
        Long domainExtensionVersionId,
        List<Long> brandProfileVersionIds,
        List<Long> channelSpecVersionIds,
        List<Long> documentVersionIds,
        String productionMode,
        String budgetTier,
        String qualityTier,
        List<AigcSlotCountOverride> slotOverrides,
        String briefJson,
        AigcProjectCoverMode coverMode,
        Long coverFileId,
        String coverPrompt,
        String coverIdempotencyKey) {

    public AigcProjectMaterializeCommand {
        brandProfileVersionIds =
                brandProfileVersionIds == null ? List.of() : List.copyOf(brandProfileVersionIds);
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
        documentVersionIds =
                documentVersionIds == null ? List.of() : List.copyOf(documentVersionIds);
        slotOverrides = slotOverrides == null ? List.of() : List.copyOf(slotOverrides);
    }
}
