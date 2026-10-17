package com.xuejiai.aaf.module.device.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.device.DeviceMessageDirectionEnum;
import com.xuejiai.aaf.common.enums.device.DeviceMessageStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 设备可靠下行消息，即 MQTT outbox。 */
@Getter
@Setter
@Entity
@Table(name = "device_message")
public class DeviceMessage extends BaseEntity {

    /** 目标设备 ID。 */
    @CrudReference(targetResource = "device.device")
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 指令幂等键，同一 commandId 只能绑定同一内容。 */
    @Column(name = "command_id", nullable = false, length = 100)
    private String commandId;

    /** 消息信封 ID。 */
    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    /** 消息方向。枚举 {@link DeviceMessageDirectionEnum}。 */
    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    /** 能力名，例如 reminder.show。 */
    @Column(name = "capability", nullable = false, length = 100)
    private String capability;

    /** 能力动作。 */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /** 指令参数。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    /** 消息状态。枚举 {@link DeviceMessageStatusEnum}。 */
    @Column(name = "status", nullable = false, length = 24)
    private String status = DeviceMessageStatusEnum.QUEUED.getCode();

    /** 关联 ID。 */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /** 链路追踪 ID。 */
    @Column(name = "trace_id", length = 100)
    private String traceId;

    /** 载荷哈希，用于校验 commandId 内容一致性。 */
    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    /** 设备单调指令序号。 */
    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    /** 签发时间。 */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    /** 过期时间。 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Broker 已返回 PUBACK 的时间。 */
    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    /** 设备已持久化并确认接受的时间。 */
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /** 设备执行完成时间。 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 执行结果。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private String result;

    /** 失败原因。 */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
