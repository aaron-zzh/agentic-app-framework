package com.xuejiai.aaf.module.knowledge.vo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 知识库搜索请求。 */
public record KnowledgeSearchDTO(
        @NotBlank String query,
        @Min(1) @Max(20) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double threshold,
        @Pattern(regexp = "vector|keyword|hybrid") String mode) {

    public int effectiveTopK() {
        return topK == null ? 5 : topK;
    }

    public double effectiveThreshold() {
        return threshold == null ? 0.7 : threshold;
    }

    public String effectiveMode() {
        return mode == null || mode.isBlank() ? "hybrid" : mode;
    }
}
