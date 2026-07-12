package com.xuejiai.aaf.module.system.notify.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 消息模板。
 *
 * <p>系统内置通知模板，{@code code} 全局唯一，与组织无关，标注 {@link OrgIgnore} 避免被 orgFilter 误套用后静默查空。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_message_template")
@OrgIgnore
@SQLDelete(
        sql =
                "UPDATE sys_message_template SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class MessageTemplate extends BaseEntity {

    /** 模板编码（唯一） */
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    /** 模板名称 */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 渠道：SMS/EMAIL/INTERNAL */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** 邮件主题 */
    @Column(name = "subject", length = 500)
    private String subject;

    /** 模板内容（FreeMarker 语法） */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 变量定义（JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private String variables;

    /** 状态：0=禁用 1=启用 */
    @Column(name = "status", nullable = false)
    private Short status = 1;
}
