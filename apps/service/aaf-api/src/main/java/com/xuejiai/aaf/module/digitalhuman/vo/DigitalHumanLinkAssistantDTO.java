package com.xuejiai.aaf.module.digitalhuman.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 关联助理命令。 */
@Schema(description = "关联助理")
public record DigitalHumanLinkAssistantDTO(
        @NotNull @Schema(description = "助理 ID", requiredMode = Schema.RequiredMode.REQUIRED)
                Long assistantId,
        @Schema(description = "是否同步助理的模型配置，默认 false", example = "false") Boolean syncModelConfig) {}
