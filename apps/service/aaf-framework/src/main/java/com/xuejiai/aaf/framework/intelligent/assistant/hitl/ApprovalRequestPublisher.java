package com.xuejiai.aaf.framework.intelligent.assistant.hitl;

/** HITL 审批请求推送 SPI，由 WebSocket/SSE 适配器实现。 */
public interface ApprovalRequestPublisher {

    void publish(HumanApprovalService.ApprovalRequest request);
}
