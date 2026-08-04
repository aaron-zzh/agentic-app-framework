package com.xuejiai.aaf.module.ai.aigc.media.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaVersionView;

/** 媒体文件版本响应，URL 查询时动态解析。 */
public record AigcMediaVersionVO(
        Long id,
        Integer versionNo,
        Long fileId,
        String url,
        Long thumbnailFileId,
        String thumbnailUrl,
        String mimeType,
        Long size,
        Integer width,
        Integer height,
        BigDecimal duration,
        BigDecimal frameRate,
        String generationInfo,
        String checksum,
        LocalDateTime createTime)
        implements AigcMediaVersionView {}
