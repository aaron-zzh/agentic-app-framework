package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcProjectMaterializeDTO(
        @NotBlank String name,
        @NotBlank String projectTypeCode,
        @NotNull Long blueprintVersionId,
        Long domainExtensionVersionId,
        List<Long> brandProfileVersionIds,
        List<Long> channelSpecVersionIds,
        List<Long> documentVersionIds,
        @NotBlank String productionMode,
        String briefJson) {

    public AigcProjectMaterializeDTO {
        brandProfileVersionIds =
                brandProfileVersionIds == null ? List.of() : List.copyOf(brandProfileVersionIds);
        channelSpecVersionIds =
                channelSpecVersionIds == null ? List.of() : List.copyOf(channelSpecVersionIds);
        documentVersionIds =
                documentVersionIds == null ? List.of() : List.copyOf(documentVersionIds);
    }
}
