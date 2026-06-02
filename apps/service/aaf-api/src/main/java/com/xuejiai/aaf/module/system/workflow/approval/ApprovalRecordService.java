package com.xuejiai.aaf.module.system.workflow.approval;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批记录服务——记录审批操作、查询审批时间线。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRecordService {

    private final ApprovalRecordRepository approvalRecordRepository;
    private final ApprovalCommentRepository approvalCommentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 记录审批操作。
     *
     * @param processInstanceId 流程实例 ID
     * @param taskId 任务 ID
     * @param assignee 审批人
     * @param operationType 操作类型
     * @param comment 审批意见
     */
    @Transactional
    public void record(
            String processInstanceId,
            String taskId,
            String assignee,
            ApprovalRecord.OperationType operationType,
            String comment) {
        var record = new ApprovalRecord();
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setAssignee(assignee);
        record.setOperationType(operationType);
        record.setComment(comment);
        record.setOperationTime(LocalDateTime.now());
        approvalRecordRepository.save(record);
        log.info(
                "审批记录：processInstanceId={}, assignee={}, type={}",
                processInstanceId,
                assignee,
                operationType);
    }

    /**
     * 查询审批时间线（按流程实例）。
     *
     * @param processInstanceId 流程实例 ID
     * @return 审批记录列表（按时间升序）
     */
    @Transactional(readOnly = true)
    public List<ApprovalRecordVO> getTimeline(String processInstanceId) {
        return approvalRecordRepository
                .findByProcessInstanceIdOrderByOperationTimeAsc(processInstanceId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 记录催办。
     *
     * @param processInstanceId 流程实例 ID
     * @param taskId 任务 ID
     * @param urgerId 催办人
     */
    @Transactional
    public void recordUrge(String processInstanceId, String taskId, String urgerId) {
        record(processInstanceId, taskId, urgerId, ApprovalRecord.OperationType.URGE, "催办");
    }

    private ApprovalRecordVO toVO(ApprovalRecord r) {
        return new ApprovalRecordVO(
                r.getId(),
                r.getProcessInstanceId(),
                r.getTaskId(),
                r.getAssignee(),
                r.getOperationType().name(),
                r.getComment(),
                r.getOperationTime());
    }

    /** 审批记录 VO。 */
    public record ApprovalRecordVO(
            Long id,
            String processInstanceId,
            String taskId,
            String assignee,
            String operationType,
            String comment,
            LocalDateTime operationTime) {}

    /**
     * 添加审批评论。
     *
     * @param processInstanceId 流程实例 ID
     * @param taskId 任务 ID
     * @param userId 评论人
     * @param content 评论内容
     * @param attachments 附件 URL 列表
     * @param mentionedUsers @提及的用户列表
     */
    @Transactional
    public ApprovalComment addComment(
            String processInstanceId,
            String taskId,
            String userId,
            String content,
            List<String> attachments,
            List<String> mentionedUsers) {
        var comment = new ApprovalComment();
        comment.setProcessInstanceId(processInstanceId);
        comment.setTaskId(taskId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setAttachments(toJson(attachments));
        comment.setMentionedUsers(toJson(mentionedUsers));
        comment.setCreateTime(LocalDateTime.now());
        return approvalCommentRepository.save(comment);
    }

    /** 查询流程实例的所有评论。 */
    @Transactional(readOnly = true)
    public List<ApprovalComment> listComments(String processInstanceId) {
        return approvalCommentRepository.findByProcessInstanceIdOrderByCreateTimeAsc(
                processInstanceId);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
