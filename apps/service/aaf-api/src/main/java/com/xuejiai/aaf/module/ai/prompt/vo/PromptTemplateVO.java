package com.xuejiai.aaf.module.ai.prompt.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 提示词资产响应。 */
public record PromptTemplateVO(
        Long id,
        Integer version,
        String type,
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
        String visibility,
        Integer usageCount,
        String scope,
        String description,
        List<String> variables,
        Long ownerId,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
