package com.xuejiai.aaf.module.system.workflow.approval;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批评论（含附件和 @提及）。
 *
 * @author Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "approval_comment")
public class ApprovalComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 流程实例 ID */
    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    /** 任务 ID */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /** 评论人 */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 评论内容 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 附件列表（JSON 数组，存文件 URL） */
    @Column(name = "attachments", columnDefinition = "TEXT")
    private String attachments;

    /** @提及的用户（JSON 数组） */
    @Column(name = "mentioned_users", columnDefinition = "TEXT")
    private String mentionedUsers;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
