package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcDeliverableSetCompletionView(
        Long setObjectId,
        Long graphRevision,
        List<Long> includedProjectObjectIds,
        boolean complete,
        List<AigcDeliverableSlotEvidence> slots,
        List<String> blockers,
        String evidenceHash) {

    public AigcDeliverableSetCompletionView {
        includedProjectObjectIds = List.copyOf(includedProjectObjectIds);
        slots = List.copyOf(slots);
        blockers = List.copyOf(blockers);
    }
}
