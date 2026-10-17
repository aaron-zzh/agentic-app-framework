package com.xuejiai.aaf.module.elder.rehab.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 康复指导更新请求。 */
@Schema(description = "更新康复指导")
public record RehabGuideUpdateDTO(
        Patch<String> title,
        Patch<String> category,
        Patch<String> summary,
        Patch<String> applicableAudience,
        Patch<Long> knowledgeDocumentId,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public RehabGuideUpdateDTO {
        title = normalize(title);
        category = normalize(category);
        summary = normalize(summary);
        applicableAudience = normalize(applicableAudience);
        knowledgeDocumentId = normalize(knowledgeDocumentId);
    }

    @JsonCreator
    public static RehabGuideUpdateDTO fromJson(
            @JsonProperty("title") JsonNode title,
            @JsonProperty("category") JsonNode category,
            @JsonProperty("summary") JsonNode summary,
            @JsonProperty("applicableAudience") JsonNode applicableAudience,
            @JsonProperty("knowledgeDocumentId") JsonNode knowledgeDocumentId,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new RehabGuideUpdateDTO(
                Patch.parse(title, node -> text(node, "标题")),
                Patch.parse(category, node -> text(node, "分类")),
                Patch.parse(summary, node -> text(node, "摘要")),
                Patch.parse(applicableAudience, node -> text(node, "适用人群说明")),
                Patch.parse(knowledgeDocumentId, RehabGuideUpdateDTO::positiveLong),
                expectedVersion);
    }

    private static String text(JsonNode node, String field) {
        if (!node.isString()) {
            throw new IllegalArgumentException(field + "必须是字符串");
        }
        return node.asString();
    }

    private static Long positiveLong(JsonNode node) {
        if (!node.isIntegralNumber() || node.longValue() <= 0) {
            throw new IllegalArgumentException("关联知识文档 ID 必须是正整数");
        }
        return node.longValue();
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }
}
