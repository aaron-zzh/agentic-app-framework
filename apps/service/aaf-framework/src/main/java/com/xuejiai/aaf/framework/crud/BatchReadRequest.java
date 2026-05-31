package com.xuejiai.aaf.framework.crud;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/** 批量读取请求。 */
@Schema(description = "批量读取请求")
public record BatchReadRequest(
        @NotEmpty(message = "记录 ID 不能为空") @Schema(description = "记录 ID 列表") List<Long> ids,
        @Schema(description = "字段集，默认 detail") String fieldSet) {}
