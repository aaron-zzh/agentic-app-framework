package com.xuejiai.aaf.module.content.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 内容对象版本响应。
 *
 * @author AaronZZH & Kiro
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentObjectVersionVO(
        Long id,
        Integer version,
        Long projectId,
        Long objectId,
        Integer versionNo,
        String status,
        Map<String, Object> contentPayload,
        List<String> assetRefs,
        Long executionRunId,
        String summary,
        LocalDateTime adoptedTime,
        Long supersededByVersionId,
        LocalDateTime createTime) {}
