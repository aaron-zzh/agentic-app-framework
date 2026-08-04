package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcProjectMaterializeCommand(
        Long workspaceId,
        String name,
        String projectTypeCode,
        Long blueprintVersionId,
        Long domainExtensionVersionId,
        List<Long> brandProfileVersionIds,
        List<Long> channelSpecVersionIds,
        String productionMode,
        String briefJson) {

    public AigcProjectMaterializeCommand {
        brandProfileVersionIds =
                brandProfileVersionIds == null ? List.of() : List.copyOf(brandProfileVersionIds);
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
    }
}
