package com.xuejiai.aaf.module.billing.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 权益定义字典（一个权益一条，code 驱动） */
@Getter
@Setter
@Entity
@Table(name = "billing_entitlement_def")
@SQLDelete(
        sql =
                "UPDATE billing_entitlement_def SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class EntitlementDef extends BaseEntity {

    /** 权益编码（ai_token/model_gpt4/kb_storage 等） */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** 权益名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 类型（BOOLEAN/COUNTABLE） */
    @Column(name = "type", nullable = false, length = 16)
    private String type;

    /** 计量单位（token/次/GB，BOOLEAN 为空） */
    @Column(name = "unit", length = 32)
    private String unit;

    /** 描述 */
    @Column(name = "description", length = 500)
    private String description;
}
