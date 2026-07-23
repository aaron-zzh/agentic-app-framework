package com.xuejiai.aaf.module.system.authorization;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "策略测试结果")
public record PolicyTestResultVO(
        @Schema(description = "统一授权结果") String outcome,
        @Schema(description = "结果说明") String reason,
        @Schema(description = "匹配的策略名称列表") List<String> matchedPolicies) {}
