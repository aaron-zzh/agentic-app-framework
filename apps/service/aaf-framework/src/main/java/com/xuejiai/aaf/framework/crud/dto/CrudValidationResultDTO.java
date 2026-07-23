package com.xuejiai.aaf.framework.crud.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预校验结果。 */
@Schema(description = "预校验结果")
public record CrudValidationResultDTO(
        @Schema(description = "是否通过") boolean valid,
        @Schema(description = "错误信息") List<String> errors) {

    public static CrudValidationResultDTO success() {
        return new CrudValidationResultDTO(true, List.of());
    }
}
