package com.xuejiai.aaf.module.stats.domain;

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
 * 用户行为事件实体。
 *
 * <p>不继承 BaseEntity——事件表为追加写入，无需软删除/乐观锁/审计字段。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user_event")
public class UserEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * M19：所属组织 ID，采集时从 {@code OrgContext} 写入。
     *
     * <p>行为聚合（漏斗/留存/画像）据此按组织过滤，避免组织管理员读取跨组织运营数据。 NULL 表示采集时无组织上下文（平台级用户），仅平台管理员可见。
     */
    @Column(name = "org_id")
    private Long orgId;

    /** 事件类型，对应 UserEventTypeEnum.code */
    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    /** 页面路径 */
    @Column(name = "page", length = 255)
    private String page;

    /** 操作目标（按钮ID/功能名） */
    @Column(name = "target", length = 255)
    private String target;

    /** 附加数据（JSON） */
    @Column(name = "extra", columnDefinition = "jsonb")
    private String extra;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
