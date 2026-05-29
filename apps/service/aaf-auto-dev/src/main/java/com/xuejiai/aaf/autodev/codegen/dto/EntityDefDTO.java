package com.xuejiai.aaf.autodev.codegen.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** 实体定义 DTO，用于代码生成输入。 */
public record EntityDefDTO(
        @NotBlank String name,
        @NotBlank String label,
        @NotBlank String module,
        @NotEmpty @Valid List<FieldDef> fields) {

    /** 字段定义。 */
    public record FieldDef(
            @NotBlank String name,
            @NotBlank String label,
            @NotBlank String type,
            boolean required,
            String defaultValue) {}
}
