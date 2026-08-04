package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import java.util.List;

public record AigcTimelineTrackVO(
        Long id,
        Long compositionId,
        String trackType,
        String name,
        Integer sortOrder,
        Boolean muted,
        Boolean locked,
        List<AigcTimelineClipVO> clips) {

    public AigcTimelineTrackVO {
        clips = clips == null ? List.of() : List.copyOf(clips);
    }
}
