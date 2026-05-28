package com.xuejiai.aaf.module.system.role.policy;

import io.swagger.v3.oas.annotations.media.Schema;

/** 访问策略 Response VO。 */
@Schema(description = "访问策略信息")
public record AccessPolicyVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "策略名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "条件表达式（JSON）") String conditionJson,
        @Schema(description = "效果", example = "ALLOW") String effect,
        @Schema(description = "优先级") Integer priority,
        @Schema(description = "目标资源类型") String targetResource,
        @Schema(description = "目标操作") String targetAction,
        @Schema(description = "状态") Integer status) {}
