package com.xuejiai.aaf.module.system.workflow.approval;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批记录。
 *
 * @author Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_approval_record")
@SQLDelete(
        sql =
                "UPDATE sys_approval_record SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ApprovalRecord extends BaseEntity {

    /** 流程实例 ID */
    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    /** 任务 ID */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /** 审批人标识 */
    @Column(name = "assignee", nullable = false, length = 64)
    private String assignee;

    /** 操作类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private OperationType operationType;

    /** 审批意见 */
    @Column(name = "comment", length = 500)
    private String comment;

    /** 操作时间 */
    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime;

    /** 操作类型枚举 */
    public enum OperationType {
        /** 通过 */
        APPROVE,
        /** 拒绝 */
        REJECT,
        /** 委托 */
        DELEGATE,
        /** 加签 */
        ADD_SIGN,
        /** 转签 */
        TRANSFER,
        /** 撤回 */
        WITHDRAW,
        /** 催办 */
        URGE
    }
}
