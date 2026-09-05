package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AigcDeliverableSetFreezeDTO(
        List<Long> includedOptionalObjectIds,
        @NotNull Long expectedGraphRevision,
        @NotBlank String expectedEvidenceHash,
        @NotNull Integer expectedProjectVersion,
        @NotBlank String idempotencyKey) {

    public AigcDeliverableSetFreezeDTO {
        includedOptionalObjectIds =
                includedOptionalObjectIds == null
                        ? List.of()
                        : List.copyOf(includedOptionalObjectIds);
    }
}
