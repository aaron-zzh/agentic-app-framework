package com.xuejiai.aaf.module.system.workflow.approval;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 抄送记录。
 *
 * @author Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "approval_cc_record")
@SQLDelete(
        sql =
                "UPDATE approval_cc_record SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class CarbonCopyRecord extends BaseEntity {

    /** 流程实例 ID */
    @Column(name = "process_instance_id", nullable = false, length = 64)
    private String processInstanceId;

    /** 节点名称 */
    @Column(name = "task_name", length = 128)
    private String taskName;

    /** 被抄送人 */
    @Column(name = "cc_user", nullable = false, length = 64)
    private String ccUser;

    /** 抄送时间 */
    @Column(name = "cc_time", nullable = false)
    private LocalDateTime ccTime;

    /** 关联实体类型 */
    @Column(name = "entity_type", length = 64)
    private String entityType;

    /** 关联实体 ID */
    @Column(name = "entity_id", length = 64)
    private String entityId;

    /** 是否已读 */
    @Column(name = "read", nullable = false)
    private Boolean read = false;
}
