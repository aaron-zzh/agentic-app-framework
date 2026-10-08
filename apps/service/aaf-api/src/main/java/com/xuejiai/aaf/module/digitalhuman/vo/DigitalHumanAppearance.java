package com.xuejiai.aaf.module.digitalhuman.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 数字人形象配置。 */
@Schema(description = "形象配置")
public record DigitalHumanAppearance(
        @Schema(description = "年龄") Integer age,
        @Schema(description = "性别") String gender,
        @Schema(description = "风格") String style,
        @Schema(description = "提示词") String prompt) {}
