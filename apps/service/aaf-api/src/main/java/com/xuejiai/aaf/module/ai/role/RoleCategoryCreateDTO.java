package com.xuejiai.aaf.module.ai.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建角色分类请求。
 *
 * <p>不接受 visibility、ownerId：由访问模式决定。普通访问创建私有分类，code 必须为空；治理访问创建系统分类，code 必填。
 */
@Schema(description = "创建角色分类请求")
public record RoleCategoryCreateDTO(
        @Schema(description = "稳定业务码：仅治理模式创建系统分类时填写，创建后不可修改") @Size(max = 64) String code,
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 64)
                String name,
        @Schema(description = "排序值，升序，缺省为 100") Integer sortOrder) {}
