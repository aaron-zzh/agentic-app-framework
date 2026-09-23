package com.xuejiai.aaf.module.digitalhuman.dto;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "关联助理请求")
public class LinkAssistantRequest {

    @NotNull(message = "助理 ID 不能为空")
    @Schema(description = "助理 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long assistantId;

    @Schema(description = "是否同步助理的模型配置", example = "false")
    private Boolean syncModelConfig = false;
}
