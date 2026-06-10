package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 更新内容产出请求 DTO。 */
public record AigcContentUpdateDTO(
        @Size(max = 200) @Schema(description = "标题") String title,
        @Schema(description = "关联文档 ID") Long docId,
        @Schema(description = "关联素材 ID 列表（JSON 数组）") String assetIds,
        @Schema(description = "发布平台") String platform,
        @Schema(description = "计划发布时间") LocalDateTime publishTime) {}
