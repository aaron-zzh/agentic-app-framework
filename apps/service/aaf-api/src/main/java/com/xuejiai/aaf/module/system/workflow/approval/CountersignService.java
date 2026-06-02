package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.workflow.approval.CountersignConfig.CountersignMode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会签/或签服务——创建多实例任务、计算投票结果、查询投票进度。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountersignService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    /**
     * 创建会签任务（设置 Flowable 多实例变量）。
     *
     * @param processInstanceId 流程实例 ID
     * @param config 会签配置
     */
    @Transactional
    public void setupCountersign(String processInstanceId, CountersignConfig config) {
        runtimeService.setVariable(processInstanceId, "assigneeList", config.assignees());
        runtimeService.setVariable(processInstanceId, "nrOfApproved", 0);
        runtimeService.setVariable(processInstanceId, "nrOfRejected", 0);
        runtimeService.setVariable(processInstanceId, "countersignMode", config.mode().name());
        if (config.passRatio() != null) {
            runtimeService.setVariable(processInstanceId, "passRatio", config.passRatio());
        }
        log.info(
                "会签配置完成：processInstanceId={}, mode={}, assignees={}",
                processInstanceId,
                config.mode(),
                config.assignees().size());
    }

    /**
     * 计算投票结果。
     *
     * @param processInstanceId 流程实例 ID
     * @param approved 当前投票是否通过
     * @return true=会签整体通过，false=会签整体拒绝，null=尚未结束
     */
    @Transactional
    public Boolean calculateVoteResult(String processInstanceId, boolean approved) {
        var variables = runtimeService.getVariables(processInstanceId);
        int nrOfApproved = (int) variables.getOrDefault("nrOfApproved", 0);
        int nrOfRejected = (int) variables.getOrDefault("nrOfRejected", 0);
        @SuppressWarnings("unchecked")
        var assigneeList = (List<String>) variables.get("assigneeList");
        int total = assigneeList != null ? assigneeList.size() : 0;
        var modeStr = (String) variables.getOrDefault("countersignMode", "ALL_APPROVE");
        var mode = CountersignMode.valueOf(modeStr);

        if (approved) {
            nrOfApproved++;
            runtimeService.setVariable(processInstanceId, "nrOfApproved", nrOfApproved);
        } else {
            nrOfRejected++;
            runtimeService.setVariable(processInstanceId, "nrOfRejected", nrOfRejected);
        }

        return switch (mode) {
            case ALL_APPROVE -> {
                if (nrOfRejected > 0) yield Boolean.FALSE;
                yield (nrOfApproved >= total) ? Boolean.TRUE : null;
            }
            case ANY_APPROVE -> {
                if (nrOfApproved > 0) yield Boolean.TRUE;
                yield (nrOfRejected >= total) ? Boolean.FALSE : null;
            }
            case RATIO -> {
                int completed = nrOfApproved + nrOfRejected;
                if (completed < total) yield null;
                int passRatio = (int) variables.getOrDefault("passRatio", 100);
                yield (nrOfApproved * 100 / total >= passRatio) ? Boolean.TRUE : Boolean.FALSE;
            }
        };
    }

    /**
     * 查询投票进度。
     *
     * @param processInstanceId 流程实例 ID
     * @return 投票进度信息
     */
    @Transactional(readOnly = true)
    public VoteProgress getVoteProgress(String processInstanceId) {
        var variables = runtimeService.getVariables(processInstanceId);
        int nrOfApproved = (int) variables.getOrDefault("nrOfApproved", 0);
        int nrOfRejected = (int) variables.getOrDefault("nrOfRejected", 0);
        @SuppressWarnings("unchecked")
        var assigneeList = (List<String>) variables.getOrDefault("assigneeList", List.of());
        int total = assigneeList.size();

        // 查询已完成的任务审批人
        List<String> votedAssignees =
                taskService.createTaskQuery().processInstanceId(processInstanceId).list().stream()
                        .map(Task::getAssignee)
                        .toList();

        List<String> pendingAssignees =
                assigneeList.stream().filter(a -> !votedAssignees.contains(a)).toList();

        return new VoteProgress(
                total, nrOfApproved, nrOfRejected, votedAssignees, pendingAssignees);
    }

    /**
     * 投票进度。
     *
     * @param total 总人数
     * @param approved 已通过数
     * @param rejected 已拒绝数
     * @param votedAssignees 已投票人员
     * @param pendingAssignees 未投票人员
     */
    public record VoteProgress(
            int total,
            int approved,
            int rejected,
            List<String> votedAssignees,
            List<String> pendingAssignees) {}
}
