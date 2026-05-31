package com.xuejiai.aaf.framework.crud;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 通用 CRUD 元数据。 */
@Schema(description = "通用 CRUD 元数据")
public record CrudMeta(
        @Schema(description = "实体标识") String entitySlug,
        @Schema(description = "实体名称") String entityName,
        @Schema(description = "可用字段集") List<String> fieldSets,
        @Schema(description = "可用通用操作") List<String> operations) {}
