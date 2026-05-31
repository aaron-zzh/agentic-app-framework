package com.xuejiai.aaf.framework.crud;

import io.swagger.v3.oas.annotations.media.Schema;

/** 关系字段和选择器选项。 */
@Schema(description = "选择器选项")
public record CrudOption(
        @Schema(description = "记录 ID") Long id, @Schema(description = "显示名称") String label) {}
