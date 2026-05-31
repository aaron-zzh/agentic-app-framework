package com.xuejiai.aaf.framework.crud;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 分组聚合请求。 */
@Schema(description = "分组聚合请求")
public record CrudGroupRequest(
        @NotBlank(message = "分组字段不能为空") @Schema(description = "分组字段") String groupBy,
        @Schema(description = "聚合字段，默认 id") String aggregateField,
        @Schema(description = "聚合函数，默认 count") String aggregate) {}
