package com.xuejiai.aaf.framework.crud;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 预校验结果。 */
@Schema(description = "预校验结果")
public record CrudValidationResult(
        @Schema(description = "是否通过") boolean valid,
        @Schema(description = "错误信息") List<String> errors) {

    public static CrudValidationResult success() {
        return new CrudValidationResult(true, List.of());
    }
}
