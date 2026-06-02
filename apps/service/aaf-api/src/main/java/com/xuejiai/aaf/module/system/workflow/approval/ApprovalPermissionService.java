package com.xuejiai.aaf.module.system.workflow.approval;

import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.workflow.service.DelegationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批权限服务——权限检查、数据过滤、代理人检查、审批统计。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalPermissionService {

    private final DelegationService delegationService;
    private final ApprovalRecordRepository approvalRecordRepository;

    /**
     * 检查用户是否有权审批指定流程类型。
     *
     * @param userId 用户 ID
     * @param processKey 流程定义 key
     * @return true=有权限
     */
    public boolean canApprove(Long userId, String processKey) {
        // TODO: 对接权限系统检查用户是否有审批该流程类型的权限
        return true;
    }

    /**
     * 检查当前用户是否是某人的代理人。
     *
     * @param currentUserId 当前用户 ID
     * @param delegatorId 委托人 ID
     * @return true=是代理人
     */
    public boolean isDelegateOf(Long currentUserId, Long delegatorId) {
        return delegationService
                .findActiveDelegation(delegatorId)
                .map(d -> d.getDelegateId().equals(currentUserId))
                .orElse(false);
    }

    /**
     * 审批统计——个人审批效率。
     *
     * @param assignee 审批人标识
     * @return 审批统计信息
     */
    @Transactional(readOnly = true)
    public ApprovalStats getStats(String assignee) {
        var records = approvalRecordRepository.findByAssigneeOrderByOperationTimeDesc(assignee);

        long totalCount = records.size();
        long approveCount =
                records.stream()
                        .filter(r -> r.getOperationType() == ApprovalRecord.OperationType.APPROVE)
                        .count();
        long rejectCount =
                records.stream()
                        .filter(r -> r.getOperationType() == ApprovalRecord.OperationType.REJECT)
                        .count();

        // 计算平均处理时长（基于创建时间和操作时间的差值）
        double avgProcessHours =
                records.stream()
                                .filter(
                                        r ->
                                                r.getCreateTime() != null
                                                        && r.getOperationTime() != null)
                                .mapToLong(
                                        r ->
                                                ChronoUnit.MINUTES.between(
                                                        r.getCreateTime(), r.getOperationTime()))
                                .average()
                                .orElse(0.0)
                        / 60.0;

        return new ApprovalStats(totalCount, approveCount, rejectCount, avgProcessHours);
    }

    /**
     * 审批统计。
     *
     * @param totalCount 总审批数
     * @param approveCount 通过数
     * @param rejectCount 拒绝数
     * @param avgProcessHours 平均处理时长（小时）
     */
    public record ApprovalStats(
            long totalCount, long approveCount, long rejectCount, double avgProcessHours) {}
}
