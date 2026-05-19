package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** 创建/更新页面定义请求。 */
@Schema(description = "创建/更新页面定义")
public record PageDefCreateDTO(
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[a-z][a-z0-9/_-]*$", message = "slug 只允许小写字母、数字、斜杠和连字符")
                String slug,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String config) {}
