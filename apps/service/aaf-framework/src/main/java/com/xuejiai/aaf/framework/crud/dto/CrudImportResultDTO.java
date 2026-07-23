package com.xuejiai.aaf.framework.crud.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 导入结果。 */
@Schema(description = "导入结果")
public record CrudImportResultDTO(
        @Schema(description = "总行数") int total,
        @Schema(description = "成功行数") int success,
        @Schema(description = "错误信息") List<String> errors,
        @Schema(description = "是否仅校验不写入") boolean dryRun) {}
