package com.xuejiai.aaf.framework.engine.workflow;

import java.util.List;
import java.util.Map;

/**
 * 工作流引擎——流程编排与任务调度的统一抽象。
 *
 * <p>当前实现基于 Flowable，后续可替换为自研轻量引擎。 业务层通过此接口交互，不直接依赖 Flowable API。
 */
public interface WorkflowEngine {

    /** 流程实例信息 */
    record ProcessInfo(String processInstanceId, String processKey, String status) {}

    /** 任务信息 */
    record TaskInfo(String taskId, String processInstanceId, String assignee, String name) {}

    /** 历史记录 */
    record HistoryRecord(
            String taskName, String assignee, String outcome, String comment, long completedAtMs) {}

    /**
     * 启动流程实例。
     *
     * @param processKey 流程定义 key
     * @param businessKey 业务关联 key
     * @param variables 流程变量
     * @return 流程实例 ID
     */
    String startProcess(String processKey, String businessKey, Map<String, Object> variables);

    /**
     * 完成任务。
     *
     * @param taskId 任务 ID
     * @param variables 任务变量
     * @param comment 审批意见（可为 null）
     */
    void completeTask(String taskId, Map<String, Object> variables, String comment);

    /**
     * 查询流程当前待办任务。
     *
     * @param processInstanceId 流程实例 ID
     * @return 当前任务（可能为 null 表示已结束）
     */
    TaskInfo getCurrentTask(String processInstanceId);

    /**
     * 查询流程历史。
     *
     * @param processInstanceId 流程实例 ID
     * @return 历史记录列表
     */
    List<HistoryRecord> getHistory(String processInstanceId);

    /**
     * 转交任务。
     *
     * @param taskId 任务 ID
     * @param newAssignee 新处理人
     */
    void reassignTask(String taskId, String newAssignee);

    /**
     * 获取流程变量。
     *
     * @param processInstanceId 流程实例 ID
     * @return 流程变量（可能为空 Map）
     */
    java.util.Map<String, Object> getProcessVariables(String processInstanceId);
}
