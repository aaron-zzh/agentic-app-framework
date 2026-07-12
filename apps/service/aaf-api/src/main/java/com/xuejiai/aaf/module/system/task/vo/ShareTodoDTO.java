package com.xuejiai.aaf.module.system.task.vo;

import com.xuejiai.aaf.common.enums.sys.RebacRelationEnum;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 分享待办给协作者请求（L2 ReBAC 演示）。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "分享待办给协作者")
public record ShareTodoDTO(
        @NotNull @Schema(description = "协作者用户 ID") Long subjectId,
        @NotBlank
                @InEnum(value = RebacRelationEnum.class, message = "关系必须是 {value}")
                @Schema(description = "授予关系：OWNER / EDITOR / VIEWER", example = "VIEWER")
                String relation) {}
