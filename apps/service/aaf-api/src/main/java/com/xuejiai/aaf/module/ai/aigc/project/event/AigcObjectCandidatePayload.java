package com.xuejiai.aaf.module.ai.aigc.project.event;

import java.util.List;

public record AigcObjectCandidatePayload(
        String contentJson, Long documentVersionId, List<Long> mediaVersionIds) {

    public AigcObjectCandidatePayload {
        mediaVersionIds = mediaVersionIds == null ? List.of() : List.copyOf(mediaVersionIds);
    }
}
