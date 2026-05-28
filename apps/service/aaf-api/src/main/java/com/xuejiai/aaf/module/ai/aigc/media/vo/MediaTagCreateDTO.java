package com.xuejiai.aaf.module.ai.aigc.media.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 素材标签创建 Request DTO。 */
public record MediaTagCreateDTO(
        @Schema(description = "标签名称", example = "风景") @NotBlank String name,
        @Schema(description = "标签颜色（十六进制）", example = "#FF5733") String color) {}
