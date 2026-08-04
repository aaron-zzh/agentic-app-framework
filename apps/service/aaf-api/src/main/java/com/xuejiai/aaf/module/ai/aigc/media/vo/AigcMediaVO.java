package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaView;
import com.xuejiai.aaf.module.ai.aigc.media.enums.AigcMediaSourceType;

/** AIGC 媒体响应。 */
public record AigcMediaVO(
        Long id,
        String name,
        AigcMediaType mediaType,
        AigcMediaSourceType sourceType,
        Long sourceExecutionRunId,
        Long sourceTaskId,
        Long originalProjectId,
        Long assetId,
        AigcMediaVersionVO currentVersion,
        LocalDateTime createTime,
        LocalDateTime updateTime)
        implements AigcMediaView {}
