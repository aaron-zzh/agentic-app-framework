package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AigcObjectVersionVO(
        Long id,
        Long projectId,
        Long objectId,
        Integer versionNo,
        String status,
        Map<String, Object> contentPayload,
        Long documentVersionId,
        List<Long> mediaVersionIds,
        Long executionRunId,
        String summary,
        LocalDateTime adoptedTime,
        Long supersededByVersionId,
        LocalDateTime createTime) {

    public AigcObjectVersionVO {
        mediaVersionIds = mediaVersionIds == null ? List.of() : List.copyOf(mediaVersionIds);
    }
}
