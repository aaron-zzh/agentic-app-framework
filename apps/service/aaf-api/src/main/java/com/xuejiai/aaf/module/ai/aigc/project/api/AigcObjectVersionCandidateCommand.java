package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

public record AigcObjectVersionCandidateCommand(
        Long projectId,
        Long objectId,
        Long executionRunId,
        String contentJson,
        Long documentVersionId,
        List<Long> mediaVersionIds) {

    public AigcObjectVersionCandidateCommand {
        mediaVersionIds = mediaVersionIds == null ? List.of() : List.copyOf(mediaVersionIds);
    }
}
