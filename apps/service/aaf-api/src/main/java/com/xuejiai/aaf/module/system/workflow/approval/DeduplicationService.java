package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批人去重服务——避免同一流程中同一人重复审批。
 *
 * @author Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeduplicationService {

    private final ApprovalRecordRepository approvalRecordRepository;

    /**
     * 对审批人列表去重，移除已在该流程实例中审批过的人。
     *
     * @param processInstanceId 流程实例 ID
     * @param assignees 候选审批人列表
     * @return 去重后的审批人列表（如果去重后为空则返回原列表）
     */
    public List<String> deduplicateAssignees(String processInstanceId, List<String> assignees) {
        if (assignees == null || assignees.isEmpty()) return assignees;

        Set<String> alreadyApproved = approvalRecordRepository
                .findByProcessInstanceIdOrderByOperationTimeAsc(processInstanceId)
                .stream()
                .map(ApprovalRecord::getAssignee)
                .collect(Collectors.toSet());

        var deduplicated = assignees.stream()
                .filter(a -> !alreadyApproved.contains(a))
                .toList();

        if (deduplicated.isEmpty()) {
            log.warn("去重后无可用审批人，返回原列表: processInstance={}", processInstanceId);
            return assignees;
        }

        log.info("审批人去重: processInstance={}, 原={}, 去重后={}",
                processInstanceId, assignees.size(), deduplicated.size());
        return deduplicated;
    }
}
