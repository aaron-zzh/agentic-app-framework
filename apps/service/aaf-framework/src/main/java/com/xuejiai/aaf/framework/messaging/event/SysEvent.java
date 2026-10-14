package com.xuejiai.aaf.framework.messaging.event;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 全局事件通道的持久事件记录，{@code id} 即 SSE 游标。
 *
 * <p>{@code orgId} 与 {@code workspaceId} 都为空表示用户级事件（投递给该用户所有连接）。范围匹配由 {@link SysEventRepository}
 * 显式完成，因此不套用通用组织过滤器；后台线程无组织上下文时也能发布与补发。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_event")
@OrgIgnore
public class SysEvent extends BaseEntity {

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refs", nullable = false, columnDefinition = "jsonb")
    private Map<String, Long> refs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;
}
