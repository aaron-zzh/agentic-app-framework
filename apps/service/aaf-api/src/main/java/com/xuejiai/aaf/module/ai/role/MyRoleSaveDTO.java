package com.xuejiai.aaf.module.ai.role;

import java.util.List;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 用户创建、更新私有 Role 的请求。
 *
 * <p>不接受 visibility、ownerId、code、status：全部由服务端决定，避免用户越权声明可见性或归属。
 */
@Schema(description = "创建、更新私有 Role 请求")
public record MyRoleSaveDTO(
        @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 128)
                String name,
        @Schema(description = "描述，用于模型按任务选择角色") @Size(max = 512) String description,
        @Schema(description = "Skill 绑定对象数组", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                List<SkillBinding> skillBindings,
        @Schema(description = "工具授权池（JSON 字符串数组）") String toolWhitelist,
        @Schema(description = "绑定知识库的 stable_id，最多 5 个") @Size(max = 5) List<UUID> knowledgeBaseIds,
        @Schema(description = "角色分类 ID：系统分类或自己的私有分类；为空表示未分类") Long categoryId) {

    public MyRoleSaveDTO {
        skillBindings = SkillBinding.copyOf(skillBindings, "skillBindings");
    }
}
