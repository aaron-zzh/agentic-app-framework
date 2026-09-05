package com.xuejiai.aaf.module.ai.aigc.timeline.api;

import java.util.List;

public record AigcTimelineReplaceCommand(
        Long projectId,
        Long timelineId,
        Integer expectedProjectVersion,
        Integer expectedVersion,
        List<AigcTimelineTrackInput> tracks) {

    public AigcTimelineReplaceCommand {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }
}
