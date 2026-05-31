package com.xuejiai.aaf.framework.crud;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/** JSON 导入请求。 */
@Schema(description = "JSON 导入请求")
public record CrudImportRequest<T>(
        @NotEmpty(message = "导入数据不能为空") @Schema(description = "导入数据") List<T> rows,
        @Schema(description = "是否仅校验不写入") Boolean dryRun) {}
