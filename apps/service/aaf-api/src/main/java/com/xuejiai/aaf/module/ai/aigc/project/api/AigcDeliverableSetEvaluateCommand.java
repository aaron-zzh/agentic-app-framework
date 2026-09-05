package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcDeliverableSetEvaluateCommand(
        Long projectId,
        Long setObjectId,
        List<Long> includedOptionalObjectIds,
        Long expectedGraphRevision) {

    public AigcDeliverableSetEvaluateCommand {
        includedOptionalObjectIds =
                includedOptionalObjectIds == null
                        ? List.of()
                        : List.copyOf(includedOptionalObjectIds);
    }
}
