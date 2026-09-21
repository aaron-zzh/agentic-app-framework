package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.time.Instant;
import java.time.ZoneId;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * AIGC 项目定时任务。
 *
 * <p>一次性项目 AI 定时任务，支持指定时间点触发 Assistant Task。
 *
 * <h3>状态流转</h3>
 * <pre>
 * PENDING → DISPATCHING → TRIGGERED（成功）
 *                       ↘ FAILED（失败）
 *                       ↘ DEFERRED（等待当前任务完成）
 *
 * PENDING → CANCELLED（用户取消）
 * DEFERRED → DISPATCHING（当前任务完成后重试）
 * </pre>
 *
 * <h3>分布式调度</h3>
 * <p>由统一 scheduler 扫描 due 记录，使用 {@code FOR UPDATE SKIP LOCKED} + claim fence 原子认领。
 *
 * @author Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_schedule")
@SQLDelete(
        sql =
                "UPDATE aigc_project_schedule SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcProjectSchedule extends BaseEntity {

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 执行时间（UTC Instant） */
    @Column(name = "execute_at", nullable = false)
    private Instant executeAt;

    /** IANA 时区（如 Asia/Shanghai） */
    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "assistant_id", nullable = false, length = 64)
    private String assistantId;

    @Column(name = "conversation_id", nullable = false, length = 128)
    private String conversationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    /** 受信项目范围快照（projectId, workspaceId, graphRevision 等） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "project_scope", columnDefinition = "jsonb")
    private ProjectScopeSnapshot projectScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING;

    /** 下次尝试时间（DEFERRED 状态使用） */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** 分布式认领围栏 */
    @Column(name = "claim_fence", length = 64)
    private String claimFence;

    /** 触发的 canonical Task ID */
    @Column(name = "linked_task_id", length = 128)
    private String linkedTaskId;

    /** 最后失败原因 */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    public enum Status {
        /** 待触发 */
        PENDING,
        /** 触发中 */
        DISPATCHING,
        /** 等待当前任务完成 */
        DEFERRED,
        /** 已触发 */
        TRIGGERED,
        /** 已取消 */
        CANCELLED,
        /** 失败 */
        FAILED
    }

    /** 项目范围快照 */
    public record ProjectScopeSnapshot(
            Long projectId, Long workspaceId, Integer graphRevision, String projectName) {}

    /** 是否为终态 */
    public boolean isTerminal() {
        return status == Status.TRIGGERED
                || status == Status.CANCELLED
                || status == Status.FAILED;
    }

    /** 获取解析后的时区 */
    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }
}
