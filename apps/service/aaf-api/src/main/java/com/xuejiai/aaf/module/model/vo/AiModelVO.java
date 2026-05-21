package com.xuejiai.aaf.module.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模型详情 VO（不含 apiKey 明文）。 */
public record AiModelVO(
        Long id,
        String modelId,
        String displayName,
        String provider,
        String providerType,
        String modelName,
        String baseUrl,
        /** apiKey 是否已配置（不返回明文） */
        boolean apiKeyConfigured,
        String capabilities,
        Double temperature,
        Integer maxTokens,
        Integer contextWindow,
        BigDecimal inputPricePerK,
        BigDecimal outputPricePerK,
        Boolean enabled,
        String fallbackModelId,
        Integer sortOrder,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
