package com.xuejiai.aaf.module.system.workflow.approval;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批表单模板——定义审批流程关联的表单字段。
 *
 * @author AaronZZH
 */
@Getter
@Setter
@Entity
@Table(name = "approval_form_template")
@SQLDelete(
        sql =
                "UPDATE approval_form_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ApprovalFormTemplate extends BaseEntity {

    /** 模板名称 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 模板描述 */
    @Column(name = "description", length = 500)
    private String description;

    /** 关联流程定义 Key */
    @Column(name = "process_key", nullable = false, length = 128)
    private String processKey;

    /** 表单字段定义（JSON） */
    @Column(name = "fields_json", columnDefinition = "TEXT")
    private String fieldsJson;

    /** 状态：0-禁用，1-启用 */
    @Column(name = "status", nullable = false)
    private Integer status = 1;
}
