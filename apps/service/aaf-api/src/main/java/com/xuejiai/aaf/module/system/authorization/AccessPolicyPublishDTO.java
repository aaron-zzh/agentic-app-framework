package com.xuejiai.aaf.module.system.authorization;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "策略发布请求")
public record AccessPolicyPublishDTO(
        @Schema(description = "发布模式：SHADOW/ENFORCE") @NotBlank String mode) {}
