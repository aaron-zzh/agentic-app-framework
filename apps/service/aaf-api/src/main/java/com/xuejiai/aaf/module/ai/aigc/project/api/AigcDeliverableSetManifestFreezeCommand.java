package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcDeliverableSetManifestFreezeCommand(
        Long projectId,
        Long setObjectId,
        List<Long> includedOptionalObjectIds,
        Long expectedGraphRevision,
        String expectedEvidenceHash,
        Integer expectedProjectVersion,
        String idempotencyKey) {

    public AigcDeliverableSetManifestFreezeCommand {
        includedOptionalObjectIds =
                includedOptionalObjectIds == null
                        ? List.of()
                        : List.copyOf(includedOptionalObjectIds);
    }
}
