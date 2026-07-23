package com.xuejiai.aaf.module.system.authorization;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "访问策略信息")
public record AccessPolicyVO(
        @Schema(description = "编号") Long id,
        @Schema(description = "策略名称") String name,
        @Schema(description = "描述") String description,
        @Schema(description = "安全 JSON 条件") String conditionJson,
        @Schema(description = "attributes.* 事实类型白名单") Map<String, String> factSchema,
        @Schema(description = "效果", example = "ALLOW") String effect,
        @Schema(description = "优先级") Integer priority,
        @Schema(description = "目标资源类型") String targetResource,
        @Schema(description = "目标操作") String targetAction,
        @Schema(description = "生命周期") String lifecycle,
        @Schema(description = "已发布版本") Long publishedVersion) {}
