package com.xuejiai.aaf.module.ai.aigc.brand.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 创建品牌/IP 资料根。 */
@Schema(description = "创建 AIGC 品牌/IP 资料")
public record AigcBrandProfileCreateDTO(
        @NotBlank String name, @NotBlank String kind, String industry, @NotBlank String status) {}
