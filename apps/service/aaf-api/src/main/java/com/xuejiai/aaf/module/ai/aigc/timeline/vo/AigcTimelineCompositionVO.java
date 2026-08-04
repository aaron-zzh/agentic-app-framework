package com.xuejiai.aaf.module.ai.aigc.timeline.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AigcTimelineCompositionVO(
        Long id,
        Integer version,
        Long projectId,
        Long deliverableObjectId,
        String title,
        Long durationMs,
        BigDecimal fps,
        Integer width,
        Integer height,
        String status,
        Integer adoptedRevisionNo,
        List<AigcTimelineTrackVO> tracks,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public AigcTimelineCompositionVO {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }
}
