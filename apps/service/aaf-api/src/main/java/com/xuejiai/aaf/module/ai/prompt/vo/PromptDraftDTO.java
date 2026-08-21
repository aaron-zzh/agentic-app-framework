package com.xuejiai.aaf.module.ai.prompt.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Prompt Draft 创建或编辑请求；创建时未提供的内容字段继承当前发布版本。 */
public record PromptDraftDTO(
        @Size(max = 100_000) String prompt,
        @Size(max = 100_000) String negativePrompt,
        @Size(max = 100) String model,
        Integer width,
        Integer height,
        Integer steps,
        Long seed,
        @Schema(description = "模板变量名；为空时沿用或由服务端推导") @Size(max = 50)
                List<@Size(max = 64) String> variables,
        @Size(max = 512) String changeSummary) {}
