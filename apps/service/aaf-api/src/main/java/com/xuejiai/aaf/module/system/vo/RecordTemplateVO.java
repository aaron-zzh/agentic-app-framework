package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 记录模板响应。 */
@Schema(description = "记录模板信息")
public record RecordTemplateVO(
        Long id,
        String entitySlug,
        String name,
        String fieldValues,
        Boolean isShared,
        Boolean isDefault,
        Long createBy,
        LocalDateTime createTime) {}
