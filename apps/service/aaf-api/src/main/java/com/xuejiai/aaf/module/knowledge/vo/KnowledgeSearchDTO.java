package com.xuejiai.aaf.module.knowledge.vo;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeSearchContracts.SourceFilters;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 授权多库搜索请求。 */
public record KnowledgeSearchDTO(
        @NotBlank String query,
        Set<UUID> knowledgeBaseIds,
        Boolean includePublic,
        Map<UUID, Double> knowledgeBaseWeights,
        Map<String, Object> sourceFilters,
        @Min(1) @Max(50) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double threshold,
        @Pattern(regexp = "vector|keyword|graph|hybrid") String mode) {

    public KnowledgeSearchDTO {
        knowledgeBaseWeights =
                KnowledgeSearchContracts.validateKnowledgeBaseWeights(knowledgeBaseWeights);
    }

    public Set<UUID> effectiveKnowledgeBaseIds() {
        return knowledgeBaseIds == null ? Set.of() : Set.copyOf(knowledgeBaseIds);
    }

    public boolean effectiveIncludePublic() {
        return includePublic == null || includePublic;
    }

    public Map<UUID, Double> effectiveKnowledgeBaseWeights() {
        return KnowledgeSearchContracts.validateKnowledgeBaseWeights(knowledgeBaseWeights);
    }

    public Map<String, Object> effectiveSourceFilters() {
        return SourceFilters.from(sourceFilters).toMap();
    }

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
