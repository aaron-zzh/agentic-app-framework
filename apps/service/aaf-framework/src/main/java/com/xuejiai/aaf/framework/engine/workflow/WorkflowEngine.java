package com.xuejiai.aaf.framework.engine.workflow;

import java.util.List;
import java.util.Map;

/**
 * 工作流引擎——流程编排与任务调度的统一抽象。
 *
 * <p>当前实现基于 Flowable，后续可替换为自研轻量引擎。 业务层通过此接口交互，不直接依赖 Flowable API。
 */
public interface WorkflowEngine {

    // ==================== 基础 Record ====================

    /** 流程实例信息 */
    record ProcessInfo(String processInstanceId, String processKey, String status) {}

    /** 任务信息 */
    record TaskInfo(String taskId, String processInstanceId, String assignee, String name) {}

    /** 历史记录 */
    record HistoryRecord(
            String taskName, String assignee, String outcome, String comment, long completedAtMs) {}

    /** 流程定义信息 */
    record DefinitionInfo(
            String processKey, String name, int version, String id, boolean suspended) {}

    /** 流程实例详情 */
    record InstanceInfo(
            String processInstanceId,
            String processKey,
            String businessKey,
            String status,
            long startTimeMs,
            Long endTimeMs) {}

    // ==================== 原有方法 ====================

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
    Map<String, Object> getProcessVariables(String processInstanceId);

    /**
     * 查询指定审批人的待办任务列表。
     *
     * @param assignee 审批人标识
     * @return 待办任务列表
     */
    List<TaskInfo> listPendingTasks(String assignee);

    /**
     * 查询所有流程定义（最新版本）。
     *
     * @return 流程定义列表
     */
    List<DefinitionInfo> listDefinitions();

    /**
     * 部署流程定义。
     *
     * @param name 流程名称
     * @param bpmnXml BPMN XML 内容
     * @return 部署 ID
     */
    String deploy(String name, String bpmnXml);

    /**
     * 按 businessKey 查找运行中的流程实例 ID。
     *
     * @param businessKey 业务关联 key
     * @return 流程实例 ID（无匹配返回 null）
     */
    String findInstanceByBusinessKey(String businessKey);

    // ==================== #5802 流程定义管理 ====================

    /**
     * 按条件分页查询流程定义。
     *
     * @param key 流程 key（可为 null）
     * @param name 流程名称模糊匹配（可为 null）
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 流程定义列表
     */
    List<DefinitionInfo> queryDefinitions(String key, String name, int pageNo, int pageSize);

    /**
     * 查询流程定义总数（配合分页）。
     *
     * @param key 流程 key（可为 null）
     * @param name 流程名称模糊匹配（可为 null）
     * @return 总数
     */
    long countDefinitions(String key, String name);

    /**
     * 查询指定 key 的所有版本。
     *
     * @param processKey 流程定义 key
     * @return 所有版本列表
     */
    List<DefinitionInfo> listDefinitionVersions(String processKey);

    /**
     * 挂起流程定义。
     *
     * @param processDefinitionId 流程定义 ID
     */
    void suspendDefinition(String processDefinitionId);

    /**
     * 激活流程定义。
     *
     * @param processDefinitionId 流程定义 ID
     */
    void activateDefinition(String processDefinitionId);

    /**
     * 删除流程定义。
     *
     * @param deploymentId 部署 ID
     * @param cascade 是否级联删除实例
     */
    void deleteDeployment(String deploymentId, boolean cascade);

    /**
     * 导出流程定义 XML。
     *
     * @param processDefinitionId 流程定义 ID
     * @return BPMN XML 字符串
     */
    String exportDefinitionXml(String processDefinitionId);

    // ==================== #5803 流程实例管理 ====================

    /**
     * 分页查询运行中的流程实例。
     *
     * @param processKey 流程 key（可为 null）
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 实例列表
     */
    List<InstanceInfo> listRunningInstances(String processKey, int pageNo, int pageSize);

    /**
     * 查询运行中实例总数。
     *
     * @param processKey 流程 key（可为 null）
     * @return 总数
     */
    long countRunningInstances(String processKey);

    /**
     * 分页查询历史流程实例（已完成/已终止）。
     *
     * @param processKey 流程 key（可为 null）
     * @param finished 是否已完成（true=已完成，false=全部历史）
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 实例列表
     */
    List<InstanceInfo> listHistoricInstances(
            String processKey, boolean finished, int pageNo, int pageSize);

    /**
     * 查询历史实例总数。
     *
     * @param processKey 流程 key（可为 null）
     * @param finished 是否已完成
     * @return 总数
     */
    long countHistoricInstances(String processKey, boolean finished);

    /**
     * 挂起流程实例。
     *
     * @param processInstanceId 流程实例 ID
     */
    void suspendInstance(String processInstanceId);

    /**
     * 激活流程实例。
     *
     * @param processInstanceId 流程实例 ID
     */
    void activateInstance(String processInstanceId);

    /**
     * 终止流程实例。
     *
     * @param processInstanceId 流程实例 ID
     * @param reason 终止原因
     */
    void terminateInstance(String processInstanceId, String reason);

    /**
     * 删除流程实例。
     *
     * @param processInstanceId 流程实例 ID
     * @param reason 删除原因
     */
    void deleteInstance(String processInstanceId, String reason);

    /**
     * 设置流程变量。
     *
     * @param processInstanceId 流程实例 ID
     * @param variables 变量 Map
     */
    void setProcessVariables(String processInstanceId, Map<String, Object> variables);

    // ==================== #5804 任务分配与流转 ====================

    /**
     * 查询候选人待签收任务。
     *
     * @param candidateUser 候选人
     * @return 任务列表
     */
    List<TaskInfo> listCandidateTasks(String candidateUser);

    /**
     * 查询候选组待签收任务。
     *
     * @param candidateGroup 候选组
     * @return 任务列表
     */
    List<TaskInfo> listCandidateGroupTasks(String candidateGroup);

    /**
     * 查询我发起的流程实例。
     *
     * @param initiator 发起人
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 实例列表
     */
    List<InstanceInfo> listMyInitiatedInstances(String initiator, int pageNo, int pageSize);

    /**
     * 签收任务（候选人认领）。
     *
     * @param taskId 任务 ID
     * @param userId 签收人
     */
    void claimTask(String taskId, String userId);

    /**
     * 委派任务（保留原处理人，委派给他人处理后自动回到原处理人）。
     *
     * @param taskId 任务 ID
     * @param delegateUserId 被委派人
     */
    void delegateTask(String taskId, String delegateUserId);

    /**
     * 退回任务（将任务退回到上一个已完成的用户任务节点）。
     *
     * @param taskId 任务 ID
     * @param reason 退回原因
     */
    void returnTask(String taskId, String reason);

    /**
     * 催办（记录催办时间，不实现定时器）。
     *
     * @param taskId 任务 ID
     * @param urgerId 催办人
     */
    void urgeTask(String taskId, String urgerId);

    // ==================== #5805 信号与消息事件 ====================

    /**
     * 发送信号事件。
     *
     * @param signalName 信号名称
     */
    void sendSignal(String signalName);

    /**
     * 发送信号事件（带变量）。
     *
     * @param signalName 信号名称
     * @param variables 变量
     */
    void sendSignal(String signalName, Map<String, Object> variables);

    /**
     * 发送消息事件。
     *
     * @param messageName 消息名称
     * @param processInstanceId 目标流程实例 ID
     * @param variables 变量
     */
    void sendMessage(String messageName, String processInstanceId, Map<String, Object> variables);

    // ==================== 审批操作 ====================

    /**
     * 加签——向当前任务所在的多实例节点动态追加审批人。
     *
     * @param taskId 当前任务 ID
     * @param assignee 加签审批人
     */
    void addSign(String taskId, String assignee);

    /**
     * 转签——将任务转交给其他人处理。
     *
     * @param taskId 任务 ID
     * @param targetAssignee 目标审批人
     * @param reason 转签原因（可为 null）
     */
    void transferSign(String taskId, String targetAssignee, String reason);

    /**
     * 撤回——发起人撤回流程（后续节点已处理则抛出异常）。
     *
     * @param processInstanceId 流程实例 ID
     * @param initiator 发起人标识
     */
    void withdraw(String processInstanceId, String initiator);
}
