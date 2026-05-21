package com.xuejiai.aaf.module.system.log.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.SQLDelete;

/** 短信模板配置，管理签名和厂商模板 ID。 */
@Getter
@Setter
@Entity
@Table(name = "sys_sms_template")
@SQLDelete(sql = "UPDATE sys_sms_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class SmsTemplate extends BaseEntity {

    /** 业务场景编码，调用方使用 */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** 模板名称 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 签名，为空则使用系统默认签名 */
    @Column(length = 64)
    private String signName;

    /** 厂商模板 ID */
    @Column(nullable = false, length = 64)
    private String apiTemplateId;

    /** 参数名列表（JSON 数组，如 ["code","expireMinutes"]） */
    @Column(length = 255)
    private String params;

    /** 指定厂商（null=使用系统默认） */
    @Column(length = 20)
    private String provider;

    /** 状态：1=启用 0=禁用 */
    @Column(nullable = false)
    private Short status = 1;
}
