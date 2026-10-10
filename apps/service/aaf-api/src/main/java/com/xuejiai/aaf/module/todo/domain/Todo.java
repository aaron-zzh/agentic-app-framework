package com.xuejiai.aaf.module.todo.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum;
import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 待办事项。
 *
 * <p>标准业务实体示例：继承 {@link BaseEntity} 获得租户、所有者、版本和软删除字段；{@code @CrudReference} 声明字段之间的引用（目标资源与可用能力），
 * 框架据此统一校验引用权限并装配输出中的引用对象；{@code @SQLDelete} 让单条删除走软删除。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_todo")
@CrudReference(
        key = "createBy",
        idProperty = "createBy",
        targetResource = "system.user",
        viewField = "createBy",
        capabilities = ReferenceCapability.READ)
@CrudReference(
        key = "updateBy",
        idProperty = "updateBy",
        targetResource = "system.user",
        viewField = "updateBy",
        capabilities = ReferenceCapability.READ)
@SQLDelete(sql = "UPDATE sys_todo SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Todo extends BaseEntity {

    /** 执行人 ID */
    @CrudReference(targetResource = "system.user", additionalPolicyBean = "todoUserReferencePolicy")
    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    /** 待办标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 来源类型（如 comment / task） */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /** 来源资源标识 */
    @Column(name = "source_entity", length = 100)
    private String sourceEntity;

    /** 来源实体 ID */
    @CrudReference(resourceProperty = "sourceEntity")
    @Column(name = "source_id")
    private Long sourceId;

    /** 状态。枚举 {@link TodoStatusEnum} */
    @Column(name = "status", nullable = false, length = 20)
    private String status = TodoStatusEnum.PENDING.getCode();

    /** 待办分类。枚举 {@link TodoCategoryEnum}，对应字典 sys_todo_category */
    @Column(name = "category", length = 20)
    private String category = TodoCategoryEnum.TODO.getCode();

    /** 截止日期 */
    @Column(name = "due_date")
    private LocalDateTime dueDate;
}
