package com.xuejiai.aaf.module.ai.aigc.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 更新分镜规划请求 DTO。 */
public record AigcStoryboardUpdateDTO(
        @Size(max = 200) @Schema(description = "标题") String title,
        @Schema(description = "状态") String status,
        @Schema(description = "关联文档 ID") Long docId) {}
