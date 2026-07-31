package com.xuejiai.aaf.framework.engine.bpmn.api;

import java.util.List;
import java.util.Map;

/**
 * 工作流引擎——流程编排与任务调度的统一抽象。
 *
 * <p>当前实现基于 Flowable，后续可替换为自研轻量引擎。 业务层通过此接口交互，不直接依赖 Flowable API。
 */
public interface BpmnEngine {

    // ==================== 基础 Record ====================

    /** 流程实例信息 */
    record ProcessInfo(String processInstanceId, String processKey, String status) {}

    /** 任务信息 */
    record TaskInfo(String taskId, String processInstanceId, String assignee, String name) {}

    /** 历史记录 */
    record HistoryRecord(
            String taskName,
            String assignee,
            Map<String, Object> variables,
            String comment,
            long completedAtMs) {}

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
     * @param comment 任务评论（可为 null）
     */
    void completeTask(String taskId, Map<String, Object> variables, String comment);

    /**
     * 按 ID 查询运行中的任务。
     *
     * @param taskId 任务 ID
     * @return 任务信息，不存在时返回 null
     */
    TaskInfo getTask(String taskId);

    /**
     * 判断运行中任务是否位于指定流程定义和任务节点。
     *
     * @param taskId 任务 ID
     * @param processKey 流程定义 key
     * @param taskDefinitionKey 任务节点定义 key
     * @return true=任务位于指定流程和节点
     */
    boolean isTaskAt(String taskId, String processKey, String taskDefinitionKey);

    /**
     * 判断用户是否是任务处理人或候选人。
     *
     * @param taskId 任务 ID
     * @param userId 用户标识
     * @return true=可以处理该任务
     */
    boolean canOperateTask(String taskId, String userId);

    /**
     * 判断用户是否参与了指定流程实例的任务。
     *
     * @param processInstanceId 流程实例 ID
     * @param userId 用户标识
     * @return true=当前或历史任务的处理人或候选人
     */
    boolean hasTaskParticipant(String processInstanceId, String userId);

    /**
     * 判断用户在指定组织与工作区中是否有指定流程定义的可操作任务。
     *
     * @param processKey 流程定义 key
     * @param userId 用户标识
     * @param orgId 组织 ID
     * @param workspaceId 工作区 ID，组织级流程使用 null
     * @return true=存在可操作任务
     */
    boolean hasOperableTask(
            String processKey, String userId, Long orgId, Long workspaceId);

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
     * 查询指定处理人的待办任务列表。
     *
     * @param assignee 处理人标识
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

    /** 判断流程实例是否仍在运行。 */
    boolean isProcessRunning(String processInstanceId);

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
    List<InstanceInfo> listRunningInstances(
            String processKey, Long orgId, Long workspaceId, int pageNo, int pageSize);

    /**
     * 查询运行中实例总数。
     *
     * @param processKey 流程 key（可为 null）
     * @param orgId 组织 ID
     * @param workspaceId 工作区 ID，组织级流程使用 null
     * @return 总数
     */
    long countRunningInstances(String processKey, Long orgId, Long workspaceId);

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
            String processKey,
            boolean finished,
            Long orgId,
            Long workspaceId,
            int pageNo,
            int pageSize);

    /**
     * 查询历史实例总数。
     *
     * @param processKey 流程 key（可为 null）
     * @param finished 是否已完成
     * @param orgId 组织 ID
     * @param workspaceId 工作区 ID，组织级流程使用 null
     * @return 总数
     */
    long countHistoricInstances(String processKey, boolean finished, Long orgId, Long workspaceId);

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
     * 按流程定义和变量条件分页查询实例。
     *
     * @param processKey 流程定义 key（可为 null）
     * @param variableEquals 变量等值条件
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 实例列表
     */
    List<InstanceInfo> listInstances(
            String processKey,
            Map<String, Object> variableEquals,
            int pageNo,
            int pageSize);

    /**
     * 统计符合流程定义和变量条件的实例。
     *
     * @param processKey 流程定义 key（可为 null）
     * @param variableEquals 变量等值条件
     * @return 实例总数
     */
    long countInstances(String processKey, Map<String, Object> variableEquals);

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
     * 将任务移动到上一个已完成的用户任务节点。
     *
     * @param taskId 任务 ID
     */
    void moveTaskToPreviousCompleted(String taskId);

    /**
     * 添加任务评论。
     *
     * @param taskId 任务 ID
     * @param type 评论类型
     * @param message 评论内容
     */
    void addTaskComment(String taskId, String type, String message);

    /**
     * 向当前任务所在的多实例节点动态追加执行。
     *
     * @param taskId 当前任务 ID
     * @param variables 新执行的变量
     */
    void addMultiInstanceExecution(String taskId, Map<String, Object> variables);

    // ==================== #5805 信号与消息事件 ====================

    /**
     * 向指定流程实例发送信号事件。
     *
     * @param signalName 信号名称
     * @param processInstanceId 目标流程实例 ID
     * @param variables 变量
     */
    void sendSignal(String signalName, String processInstanceId, Map<String, Object> variables);

    /**
     * 发送消息事件。
     *
     * @param messageName 消息名称
     * @param processInstanceId 目标流程实例 ID
     * @param variables 变量
     */
    void sendMessage(String messageName, String processInstanceId, Map<String, Object> variables);
}
