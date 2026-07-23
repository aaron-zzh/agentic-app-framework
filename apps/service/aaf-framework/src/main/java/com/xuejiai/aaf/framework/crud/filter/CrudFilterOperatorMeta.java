package com.xuejiai.aaf.framework.crud.filter;

import io.swagger.v3.oas.annotations.media.Schema;

/** 筛选操作符的客户端渲染元数据。 */
@Schema(description = "筛选操作符元数据")
public record CrudFilterOperatorMeta(
        @Schema(description = "稳定操作符值，例如 eq、in、between") String value,
        @Schema(description = "所需最少值数量") int minValues,
        @Schema(description = "允许最多值数量，null 表示不设上限") Integer maxValues) {}
