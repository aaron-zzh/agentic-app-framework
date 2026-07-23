package com.xuejiai.aaf.framework.crud.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 分组聚合结果。 */
@Schema(description = "分组聚合结果")
public record CrudGroupResultDTO(
        @Schema(description = "分组值") Object key, @Schema(description = "数量") long count) {}
