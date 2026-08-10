package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

/** 待办更新请求；所有业务字段使用 Patch 区分缺失、null 和设置值。 */
public record TodoUpdateDTO(
        @Schema(description = "待办标题") Patch<String> title,
        @Schema(description = "待办分类") Patch<String> category,
        @Schema(description = "待办状态") Patch<String> status,
        @Schema(description = "执行人用户 ID") Patch<Long> assigneeId,
        @Schema(description = "截止时间；null 清空") Patch<LocalDateTime> dueDate,
        @Schema(description = "关联来源；null 清空") Patch<ResourceReference> source,
        @Schema(description = "参与人用户 ID；null 拒绝，数组按 REPLACE 处理") Patch<List<Long>> participants,
        @NotNull @PositiveOrZero @Schema(description = "期望版本号") Integer expectedVersion) {

    public TodoUpdateDTO {
        title = normalize(title);
        category = normalize(category);
        status = normalize(status);
        assigneeId = normalize(assigneeId);
        dueDate = normalize(dueDate);
        source = normalize(source);
        participants = normalize(participants);
    }

    @JsonCreator
    public static TodoUpdateDTO fromJson(
            @JsonProperty("title") JsonNode title,
            @JsonProperty("category") JsonNode category,
            @JsonProperty("status") JsonNode status,
            @JsonProperty("assigneeId") JsonNode assigneeId,
            @JsonProperty("dueDate") JsonNode dueDate,
            @JsonProperty("source") JsonNode source,
            @JsonProperty("participants") JsonNode participants,
            @JsonProperty("expectedVersion") Integer expectedVersion) {
        return new TodoUpdateDTO(
                Patch.parse(title, node -> decodeText(node, "title")),
                Patch.parse(category, node -> decodeText(node, "category")),
                Patch.parse(status, node -> decodeText(node, "status")),
                Patch.parse(assigneeId, TodoUpdateDTO::decodeLong),
                Patch.parse(dueDate, TodoUpdateDTO::decodeDateTime),
                Patch.parse(source, TodoJsonDecoder::decodeReference),
                Patch.parse(participants, TodoJsonDecoder::decodeIds),
                expectedVersion);
    }

    private static <T> Patch<T> normalize(Patch<T> patch) {
        return patch == null ? Patch.absent() : patch;
    }

    private static String decodeText(JsonNode node, String field) {
        if (!node.isString()) {
            throw new IllegalArgumentException(field + " 必须是字符串");
        }
        return node.asString();
    }

    private static Long decodeLong(JsonNode node) {
        if (!node.isIntegralNumber()) {
            throw new IllegalArgumentException("执行人 ID 必须是整数");
        }
        return node.longValue();
    }

    private static LocalDateTime decodeDateTime(JsonNode node) {
        if (!node.isString()) {
            throw new IllegalArgumentException("截止时间必须是 ISO 日期时间");
        }
        return LocalDateTime.parse(node.asString());
    }
}
