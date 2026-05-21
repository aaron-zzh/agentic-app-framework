package com.xuejiai.aaf.module.system.config.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 系统配置。 */
@Getter
@Setter
@Entity
@Table(name = "sys_config")
@SQLDelete(
        sql = "UPDATE sys_config SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class SystemConfig extends BaseEntity {

    /** 分类（security/user/ai/sms/storage） */
    @Column(nullable = false, length = 64)
    private String category;

    /** 配置键，全局唯一（如 user.default_password） */
    @Column(nullable = false, unique = true, length = 128)
    private String configKey;

    /** 配置值 */
    @Column(columnDefinition = "TEXT")
    private String value;

    /** 默认值，value 为空时使用 */
    @Column(columnDefinition = "TEXT")
    private String defaultValue;

    /** 值类型：string / integer / boolean / json */
    @Column(nullable = false, length = 16)
    private String valueType = "string";

    /** 显示名称 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 详细说明 */
    @Column(length = 500)
    private String description;

    /** 是否前端可见（false=敏感配置不返回 value） */
    @Column(nullable = false)
    private Boolean visible = true;

    /** 是否可编辑（false=只读系统参数） */
    @Column(nullable = false)
    private Boolean editable = true;
}
