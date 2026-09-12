package com.xuejiai.aaf.framework.intelligent.assistant.model;

/** TaskPlan 冻结校验失败；safeDetail 不得包含隐藏推理或敏感输入。 */
public record PlanValidationFailure(
        String reasonCode, String nodeId, String relatedNodeId, String safeDetail) {

    public PlanValidationFailure {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode 不能为空白");
        }
        if (safeDetail == null || safeDetail.isBlank()) {
            throw new IllegalArgumentException("safeDetail 不能为空白");
        }
    }
}
