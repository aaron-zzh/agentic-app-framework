package com.xuejiai.aaf.module.system.role.policy;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 策略测试结果 Response VO。 */
@Schema(description = "策略测试结果")
public record PolicyTestResultVO(
        @Schema(description = "是否通过") boolean allowed,
        @Schema(description = "结果说明") String reason,
        @Schema(description = "匹配的策略名称列表") List<String> matchedPolicies) {}
