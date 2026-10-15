package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleVisibilityLevel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 管理端创建、更新 AI Role Request DTO；只允许 SYSTEM / PUBLIC 角色，私有角色走用户端接口。 */
@Schema(description = "管理端创建、更新 AI Role 请求")
public record RoleCreateDTO(
        @Schema(
                        description = "稳定业务码：SYSTEM 使用 system.role. 前缀，PUBLIC 使用 public.role. 前缀",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String code,
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String name,
        @Schema(description = "描述") String description,
        @Schema(description = "Skill 绑定对象数组", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                List<SkillBinding> skillBindings,
        @Schema(description = "工具授权池（JSON 数组）") String toolWhitelist,
        @Schema(
                        description = "可见性：SYSTEM / PUBLIC，创建后不可修改",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                RoleVisibilityLevel visibility,
        @Schema(description = "状态：active / disabled，缺省为 active")
                @Pattern(regexp = "active|disabled")
                String status) {

    public RoleCreateDTO {
        skillBindings = SkillBinding.copyOf(skillBindings, "skillBindings");
    }
}
