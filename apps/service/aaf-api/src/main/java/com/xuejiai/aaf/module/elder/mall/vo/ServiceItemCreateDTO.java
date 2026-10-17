package com.xuejiai.aaf.module.elder.mall.vo;

import com.xuejiai.aaf.common.enums.elder.ServiceItemStatusEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 服务项目创建请求。 */
@Schema(description = "创建服务项目")
public record ServiceItemCreateDTO(
        @NotBlank @Size(max = 200) @Schema(description = "项目名称") String name,
        @NotBlank @Size(max = 2000) @Schema(description = "项目说明") String description,
        @NotBlank @Size(max = 32) @Schema(description = "服务分类字典值") String category,
        @NotBlank @Size(max = 32) @Schema(description = "服务单位") String unit,
        @NotBlank @Size(max = 200) @Schema(description = "价格说明") String priceDescription,
        @Positive @Schema(description = "服务时长，分钟") Integer durationMinutes,
        @InEnum(value = ServiceItemStatusEnum.class, message = "服务项目状态必须是 {value}")
                String status) {}
