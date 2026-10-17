package com.xuejiai.aaf.module.elder.profile.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 长者健康条目响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "长者健康条目")
public record ElderHealthItemVO(
        Long id,
        Integer version,
        Long elderId,
        @DictFormat(DictType.Elder.HEALTH_ITEM_TYPE) String itemType,
        String name,
        String description,
        LocalDate occurredDate,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO elder,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
