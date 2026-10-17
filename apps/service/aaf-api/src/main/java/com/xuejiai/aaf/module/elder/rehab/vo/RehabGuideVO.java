package com.xuejiai.aaf.module.elder.rehab.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 康复指导响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "康复指导")
public record RehabGuideVO(
        Long id,
        Integer version,
        String title,
        @DictFormat(DictType.Elder.REHAB_GUIDE_CATEGORY) String category,
        String summary,
        String applicableAudience,
        Long knowledgeDocumentId,
        @DictFormat(DictType.Elder.REHAB_GUIDE_STATUS) String status,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO knowledgeDocument,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
