package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaSourceType;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaType;

/** Media 响应。 */
public record MediaVO(
        Long id,
        String name,
        MediaType mediaType,
        MediaSourceType sourceType,
        Long sourceExecutionRunId,
        Long sourceTaskId,
        Long originalProjectId,
        Long assetId,
        MediaVersionVO currentVersion,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
