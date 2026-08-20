package com.xuejiai.aaf.module.ai.role;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建 AI Role Request DTO。 */
@Schema(description = "创建 AI Role 请求")
public record RoleCreateDTO(
        @Schema(description = "稳定业务码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String code,
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String name,
        @Schema(description = "描述") String description,
        @Schema(description = "Skill 绑定对象数组", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                List<SkillBinding> skillBindings,
        @Schema(description = "工具授权池（JSON 数组）") String toolWhitelist) {

    public RoleCreateDTO {
        skillBindings = SkillBinding.copyOf(skillBindings, "skillBindings");
    }
}
