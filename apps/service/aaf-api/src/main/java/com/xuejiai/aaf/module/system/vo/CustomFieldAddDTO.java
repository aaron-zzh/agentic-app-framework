package com.xuejiai.aaf.module.system.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 添加自定义字段请求。 */
@Schema(description = "添加自定义字段")
public record CustomFieldAddDTO(
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字段名须小写字母开头，仅含小写字母、数字、下划线")
                @Schema(description = "字段名（列名）", example = "custom_priority")
                String name,
        @NotBlank @Schema(description = "显示标签", example = "优先级") String label,
        @NotBlank @Schema(description = "字段类型：text/number/date/select/boolean", example = "select")
                String type,
        @Schema(description = "select 类型的选项列表") List<String> options) {}
