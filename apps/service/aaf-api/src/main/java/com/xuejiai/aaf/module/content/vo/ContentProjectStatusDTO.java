package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.enums.content.ContentProjectStatusEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 内容项目状态更新请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "内容项目状态更新请求")
public record ContentProjectStatusDTO(
        @NotBlank @InEnum(ContentProjectStatusEnum.class) String status,
        @NotNull @PositiveOrZero Integer expectedVersion) {}
