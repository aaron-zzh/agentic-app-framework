package com.xuejiai.aaf.module.customerservice.model.entity;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 企微客服账号与 Assistant 的绑定关系 */
@Getter
@Setter
@Entity
@Table(name = "wecom_kf_account_binding", indexes = {@Index(columnList = "openKfId", unique = true)})
public class WecomKfAccountBinding extends BaseEntity {

    /** 企微客服账号ID */
    @Column(nullable = false, unique = true, length = 64)
    private String openKfId;

    /** 客服账号名称（便于前端展示） */
    @Column(length = 128)
    private String accountName;

    /** 绑定的 Assistant ID */
    @Column(nullable = false, length = 64)
    private String assistantId;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean enabled = true;

    /** 备注 */
    @Column(length = 256)
    private String remark;
}
