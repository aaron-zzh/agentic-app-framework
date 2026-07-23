package com.xuejiai.aaf.module.system.task.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum;
import com.xuejiai.aaf.common.validation.InEnum;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.crud.reference.ResourceReference;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;

/** 待办创建请求。 */
@Schema(description = "创建待办")
public record TodoCreateDTO(
        @NotBlank @Schema(description = "待办标题") String title,
        @InEnum(value = TodoCategoryEnum.class, message = "分类必须是 {value}") String category,
        @Positive @Schema(description = "执行人用户 ID；为空时默认当前用户") Long assigneeId,
        @Schema(description = "关联来源；缺失或 null 表示不关联记录") Patch<ResourceReference> source,
        @Schema(description = "截止时间") LocalDateTime dueDate,
        @Schema(description = "参与人用户 ID；缺失不变，null 拒绝，数组按 REPLACE 处理")
                Patch<List<Long>> participants) {

    public TodoCreateDTO {
        source = source == null ? Patch.absent() : source;
        participants = participants == null ? Patch.absent() : participants;
    }

    @JsonCreator
    public static TodoCreateDTO fromJson(
            @JsonProperty("title") String title,
            @JsonProperty("category") String category,
            @JsonProperty("assigneeId") Long assigneeId,
            @JsonProperty("source") JsonNode source,
            @JsonProperty("dueDate") LocalDateTime dueDate,
            @JsonProperty("participants") JsonNode participants) {
        return new TodoCreateDTO(
                title,
                category,
                assigneeId,
                Patch.parse(source, TodoJsonDecoder::decodeReference),
                dueDate,
                Patch.parse(participants, TodoJsonDecoder::decodeIds));
    }
}
