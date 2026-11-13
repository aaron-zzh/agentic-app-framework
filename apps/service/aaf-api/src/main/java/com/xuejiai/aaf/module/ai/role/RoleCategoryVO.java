package com.xuejiai.aaf.module.ai.role;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** 角色分类信息 Response VO。 */
@Schema(description = "角色分类信息")
public record RoleCategoryVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "稳定业务码；私有分类为空") String code,
        @Schema(description = "名称") String name,
        @Schema(description = "排序值，升序") Integer sortOrder,
        @Schema(description = "可见性：SYSTEM / PRIVATE") String visibility,
        @Schema(description = "私有分类的所有者用户 ID；系统分类为空") Long ownerId,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {

    /** 从分类实体构造输出；系统分类不返回 ownerId，避免泄露创建管理员。 */
    public static RoleCategoryVO from(AiRoleCategory category) {
        return new RoleCategoryVO(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getSortOrder(),
                category.getVisibility(),
                category.systemCategory() ? null : category.getOwnerId(),
                category.getCreateTime(),
                category.getUpdateTime());
    }
}
