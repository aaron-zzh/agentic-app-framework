package com.xuejiai.aaf.module.system.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建实体定义请求。 */
@Schema(description = "创建实体定义")
public record EntityDefCreateDTO(
        @NotBlank
                @Size(max = 100)
                @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "slug 只允许小写字母、数字和下划线")
                String slug,
        @NotBlank String config,
        Boolean enabled) {}
