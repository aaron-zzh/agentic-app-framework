package com.xuejiai.aaf.module.ai.role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillBinding;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleVisibilityLevel;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI Role 信息 Response VO。 */
@Schema(description = "AI Role（能力配置）信息")
public record RoleVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "稳定业务码") String code,
        @Schema(description = "名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "Skill 绑定对象数组") List<SkillBinding> skillBindings,
        @Schema(description = "工具授权池（JSON）") String toolWhitelist,
        @Schema(description = "绑定的知识库 stable_id") List<UUID> knowledgeBaseIds,
        @Schema(description = "状态：active / disabled") String status,
        @Schema(description = "可见性：SYSTEM / PUBLIC / PRIVATE") RoleVisibilityLevel visibility,
        @Schema(description = "私有角色的所有者用户 ID；SYSTEM / PUBLIC 为空") Long ownerId,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {

    public RoleVO {
        skillBindings = SkillBinding.copyOf(skillBindings, "skillBindings");
    }

    /** 从角色实体构造输出；SYSTEM / PUBLIC 角色不返回 ownerId，避免泄露创建人。 */
    public static RoleVO from(Role role) {
        return new RoleVO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                RoleSkillBindingJson.read(role.getSkillIds()),
                role.getToolWhitelist(),
                RoleKnowledgeBaseJson.read(role.getKnowledgeBaseIds()),
                role.getStatus(),
                role.getVisibility(),
                role.getVisibility() == RoleVisibilityLevel.PRIVATE ? role.getOwnerId() : null,
                role.getCreateTime(),
                role.getUpdateTime());
    }
}
