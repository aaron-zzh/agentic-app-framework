package com.xuejiai.aaf.module.system.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 待办状态更新请求。 */
@Schema(description = "更新待办状态")
public record TodoStatusDTO(
        @NotBlank @Schema(description = "状态：pending / done / ignored") String status) {}
