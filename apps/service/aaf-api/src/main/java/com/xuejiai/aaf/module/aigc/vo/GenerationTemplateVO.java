package com.xuejiai.aaf.module.aigc.vo;

/** 参数模板响应。 */
public record GenerationTemplateVO(
        Long id,
        String name,
        String category,
        String prompt,
        String negativePrompt,
        String model,
        Integer width,
        Integer height,
        Integer steps,
        Long seed,
        Boolean isPublic,
        Integer usageCount) {}
