package com.xuejiai.aaf.module.ai.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 更新角色分类请求；code、visibility、ownerId 创建后不可修改。 */
@Schema(description = "更新角色分类请求")
public record RoleCategoryUpdateDTO(
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 64)
                String name,
        @Schema(description = "排序值，升序，缺省为 100") Integer sortOrder) {}
