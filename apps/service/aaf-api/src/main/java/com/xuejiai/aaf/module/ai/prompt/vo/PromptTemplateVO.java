package com.xuejiai.aaf.module.ai.prompt.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 提示词资产响应；Draft 字段只在 ADMIN_MAINTENANCE 治理视图中填充。 */
public record PromptTemplateVO(
        Long id,
        String code,
        Integer version,
        String kind,
        String type,
        String name,
        List<String> categories,
        String coverUrl,
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
        Integer draftVersion,
        String draftStatus,
        String changeSummary,
        Long ownerId,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
