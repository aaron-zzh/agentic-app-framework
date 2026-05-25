package com.xuejiai.aaf.module.aigc.vo;

import jakarta.validation.constraints.NotBlank;

/** 创建参数模板请求。 */
public record GenerationTemplateCreateDTO(
        @NotBlank String name,
        String category,
        @NotBlank String prompt,
        String negativePrompt,
        String model,
        Integer width,
        Integer height,
        Integer steps,
        Long seed,
        Boolean isPublic) {}
