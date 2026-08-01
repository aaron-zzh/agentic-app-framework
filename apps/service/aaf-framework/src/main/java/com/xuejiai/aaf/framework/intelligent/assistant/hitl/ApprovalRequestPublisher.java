package com.xuejiai.aaf.framework.intelligent.assistant.hitl;

/** 审批请求推送 SPI——各渠道（AG-UI SSE、企微/钉钉/飞书卡片）实现后由 {@link ToolApprovalService} 在创建审批时调用。 */
public interface ApprovalRequestPublisher {

    void publish(ToolApprovalService.ApprovalRequest request);
}
