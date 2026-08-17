package com.xuejiai.aaf.module.ai.skill;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Skill 稳定根对象与不可变版本视图。 */
public record SkillVO(
        @Schema(description = "根对象 ID") Long id,
        @Schema(description = "根对象乐观锁版本") Integer version,
        @Schema(description = "稳定业务码") String code,
        @Schema(description = "Skill 名称") String name,
        @Schema(description = "一句话摘要") String summary,
        @Schema(description = "默认语言地区") String locale,
        @Schema(description = "可见性") String visibility,
        @Schema(description = "是否内置") Boolean builtIn,
        @Schema(description = "当前已发布版本 ID") Long currentVersionId,
        @Schema(description = "来源 Skill ID") Long sourceSkillId,
        @Schema(description = "当前已发布版本") SkillVersionVO currentVersion,
        @Schema(description = "最新创作版本") SkillVersionVO latestVersion,
        @Schema(description = "归属用户 ID") Long ownerId,
        @Schema(description = "是否归属当前用户") Boolean ownedByCurrentUser,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {

    /** 不可变 Skill 版本。 */
    public record SkillVersionVO(
            Long id,
            Integer version,
            String status,
            String content,
            String inputSchema,
            String outputSchema,
            String outputContract,
            String toolAccessMode,
            List<ToolRequirementVO> toolRequirements,
            List<ModelRequirementVO> modelRequirements,
            String changeSummary,
            String contentHash,
            Long authoredBy,
            LocalDateTime createTime) {}

    /** 工具需求视图。 */
    public record ToolRequirementVO(
            Long id,
            String toolId,
            Long toolVersion,
            String toolName,
            Boolean required,
            String usagePurpose,
            Integer sortOrder) {}

    /** 模型能力需求视图。 */
    public record ModelRequirementVO(
            Long id,
            String capability,
            Boolean required,
            Integer minimumContextTokens,
            String rationale) {}
}
