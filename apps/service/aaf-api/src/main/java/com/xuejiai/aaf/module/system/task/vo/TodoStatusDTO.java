package com.xuejiai.aaf.module.system.task.vo;

import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 待办状态更新请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新待办状态")
public record TodoStatusDTO(
        @NotBlank
                @InEnum(value = TodoStatusEnum.class, message = "状态必须是 {value}")
                @Schema(description = "状态：pending / done / ignored")
                String status) {}
