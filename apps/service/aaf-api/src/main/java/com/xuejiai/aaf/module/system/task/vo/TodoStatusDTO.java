package com.xuejiai.aaf.module.system.task.vo;

import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 待办状态更新请求。 */
@Schema(description = "更新待办状态")
public record TodoStatusDTO(
        @NotBlank
                @InEnum(value = TodoStatusEnum.class, message = "状态必须是 {value}")
                @Schema(description = "状态：pending / done / ignored")
                String status,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {}
