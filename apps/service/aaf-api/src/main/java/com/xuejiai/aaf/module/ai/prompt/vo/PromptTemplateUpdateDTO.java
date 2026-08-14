package com.xuejiai.aaf.module.ai.prompt.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 提示词资产更新请求。 */
public record PromptTemplateUpdateDTO(
        @Size(max = 128) String name,
        @Size(max = 30) String type,
        @Size(max = 64) String category,
        @Size(max = 100_000) String prompt,
        @Size(max = 100_000) String negativePrompt,
        @Size(max = 100) String model,
        Integer width,
        Integer height,
        Integer steps,
        Long seed,
        Boolean isPublic,
        @Size(max = 20) String scope,
        @Size(max = 512) String description,
        @Schema(description = "模板变量名；为空且提示词变化时由服务端重新推导") @Size(max = 50)
                List<@Size(max = 64) String> variables) {}
