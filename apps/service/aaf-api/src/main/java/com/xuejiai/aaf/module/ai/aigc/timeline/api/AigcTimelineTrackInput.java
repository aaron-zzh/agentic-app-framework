package com.xuejiai.aaf.module.ai.aigc.timeline.api;

import java.util.List;

public record AigcTimelineTrackInput(
        String trackType,
        String name,
        Integer orderNo,
        Boolean muted,
        Boolean locked,
        List<AigcTimelineClipInput> clips) {

    public AigcTimelineTrackInput {
        clips = clips == null ? List.of() : List.copyOf(clips);
    }
}
