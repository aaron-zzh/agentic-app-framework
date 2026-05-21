package com.xuejiai.aaf.module.system.task.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 待办事项。 */
@Getter
@Setter
@Entity
@Table(name = "sys_todo")
@SQLDelete(sql = "UPDATE sys_todo SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Todo extends BaseEntity {

    /** 指派人 ID */
    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    /** 待办标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 来源类型（如 comment / task） */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /** 来源实体类型 */
    @Column(name = "source_entity", length = 50)
    private String sourceEntity;

    /** 来源实体 ID */
    @Column(name = "source_id")
    private Long sourceId;

    /** 状态：pending / done / ignored */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    /** 截止日期 */
    @Column(name = "due_date")
    private LocalDateTime dueDate;
}
