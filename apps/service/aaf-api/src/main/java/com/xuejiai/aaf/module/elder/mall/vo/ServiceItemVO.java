package com.xuejiai.aaf.module.elder.mall.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 服务项目响应。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "服务项目")
public record ServiceItemVO(
        Long id,
        Integer version,
        String name,
        String description,
        @DictFormat(DictType.Elder.SERVICE_CATEGORY) String category,
        String unit,
        String priceDescription,
        Integer durationMinutes,
        @DictFormat(DictType.Elder.SERVICE_ITEM_STATUS) String status,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
