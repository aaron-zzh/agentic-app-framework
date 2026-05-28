package com.xuejiai.aaf.module.system.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建组织请求。
 *
 * @author AaronZZH & Kiro
 */
public record OrganizationCreateDTO(
        @NotBlank(message = "组织名称不能为空") @Size(max = 100) @Schema(description = "名称") String name,
        @NotBlank(message = "组织标识不能为空") @Size(max = 100) @Schema(description = "标识符")
                String slug) {}
