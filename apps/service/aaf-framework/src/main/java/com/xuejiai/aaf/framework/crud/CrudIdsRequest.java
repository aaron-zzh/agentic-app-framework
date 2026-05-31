package com.xuejiai.aaf.framework.crud;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/** 批量 ID 请求。 */
@Schema(description = "批量 ID 请求")
public record CrudIdsRequest(
        @NotEmpty(message = "记录 ID 不能为空") @Schema(description = "记录 ID 列表") List<Long> ids) {}
