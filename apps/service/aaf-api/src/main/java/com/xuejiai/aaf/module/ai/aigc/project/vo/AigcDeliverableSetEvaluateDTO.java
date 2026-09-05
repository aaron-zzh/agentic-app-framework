package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record AigcDeliverableSetEvaluateDTO(
        List<Long> includedOptionalObjectIds, @NotNull Long expectedGraphRevision) {

    public AigcDeliverableSetEvaluateDTO {
        includedOptionalObjectIds =
                includedOptionalObjectIds == null
                        ? List.of()
                        : List.copyOf(includedOptionalObjectIds);
    }
}
