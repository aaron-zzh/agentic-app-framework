package com.xuejiai.aaf.module.system.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 全局搜索结果（按实体分组）。 */
@Schema(description = "搜索结果分组")
public record SearchResultVO(
        @Schema(description = "实体标识", example = "user") String entity,
        @Schema(description = "实体显示名", example = "用户") String label,
        @Schema(description = "匹配记录") List<SearchItem> items) {

    @Schema(description = "搜索匹配项")
    public record SearchItem(
            @Schema(description = "记录 ID") Long id,
            @Schema(description = "标题/名称") String title,
            @Schema(description = "描述/副标题") String description) {}
}
