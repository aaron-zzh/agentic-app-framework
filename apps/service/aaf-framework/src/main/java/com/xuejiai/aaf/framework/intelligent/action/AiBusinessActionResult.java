package com.xuejiai.aaf.framework.intelligent.action;

import io.swagger.v3.oas.annotations.media.Schema;

/** AI 业务动作调用结果。 */
public record AiBusinessActionResult(
        @Schema(description = "是否成功") boolean success,
        @Schema(description = "结果编码") String code,
        @Schema(description = "人类可读消息") String message,
        @Schema(description = "业务数据") Object data,
        @Schema(description = "是否可恢复执行") boolean recoverable,
        @Schema(description = "是否等待授权") boolean pendingApproval,
        @Schema(description = "授权信息") Authorization authorization,
        @Schema(description = "恢复执行提示") Resume resume) {

    public static AiBusinessActionResult success(Object data) {
        return new AiBusinessActionResult(true, "OK", "执行成功", data, false, false, null, null);
    }

    public static AiBusinessActionResult failure(String code, String message) {
        return new AiBusinessActionResult(false, code, message, null, false, false, null, null);
    }

    public static AiBusinessActionResult forbidden(String message) {
        return new AiBusinessActionResult(
                false,
                "FORBIDDEN",
                message,
                null,
                true,
                false,
                new Authorization("ADMIN_REQUIRED", null, "管理员授权业务权限"),
                null);
    }

    public static AiBusinessActionResult pendingApproval(String message, String approvalId) {
        return new AiBusinessActionResult(
                false,
                "PENDING_APPROVAL",
                message,
                null,
                true,
                true,
                new Authorization("USER_APPROVAL", approvalId, "用户即时确认"),
                new Resume("WAIT_APPROVAL", approvalId, "用户确认后使用相同参数重试"));
    }

    public static AiBusinessActionResult insufficientCredits(
            String message, String entitlementCode, long estimatedCost) {
        return new AiBusinessActionResult(
                false,
                "INSUFFICIENT_CREDITS",
                message,
                java.util.Map.of("entitlementCode", entitlementCode, "estimatedCost", estimatedCost),
                true,
                false,
                new Authorization("BILLING_REQUIRED", null, "用户充值/升级"),
                new Resume("WAIT_CREDIT", entitlementCode, "额度恢复后使用相同参数重试"));
    }

    public record Authorization(String mode, String approvalId, String requiredBy) {}

    public record Resume(String strategy, String token, String instruction) {}
}
