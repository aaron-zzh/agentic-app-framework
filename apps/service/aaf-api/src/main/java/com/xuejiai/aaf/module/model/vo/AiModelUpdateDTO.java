package com.xuejiai.aaf.module.model.vo;

import java.math.BigDecimal;

/** 更新模型请求（所有字段可选，null 表示不修改）。 */
public record AiModelUpdateDTO(
        String displayName,
        String baseUrl,
        /** 传 null 不修改，传空字符串清空 apiKey */
        String apiKey,
        String capabilities,
        Double temperature,
        Integer maxTokens,
        Integer contextWindow,
        BigDecimal inputPricePerK,
        BigDecimal outputPricePerK,
        Boolean enabled,
        String fallbackModelId,
        Integer sortOrder,
        String remark) {}
