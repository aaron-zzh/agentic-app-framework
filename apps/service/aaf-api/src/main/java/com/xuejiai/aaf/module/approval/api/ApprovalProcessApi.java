package com.xuejiai.aaf.module.approval.api;

/** 审批流程跨模块接口。 */
public interface ApprovalProcessApi {

    /** 启动审批流程。 */
    String startProcess(String entityType, Long entityId, String initiator, String assignee);
}
