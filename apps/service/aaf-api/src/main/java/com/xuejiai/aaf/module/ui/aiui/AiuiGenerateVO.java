package com.xuejiai.aaf.module.ui.aiui;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI UI 生成结果 VO。 */
@Schema(description = "AI UI 生成结果")
public record AiuiGenerateVO(
        @Schema(description = "生成的 EntityDef JSON") String entityDefJson,
        @Schema(description = "模型使用说明") String explanation) {}
