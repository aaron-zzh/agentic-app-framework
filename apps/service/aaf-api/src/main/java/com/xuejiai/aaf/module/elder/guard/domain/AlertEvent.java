package com.xuejiai.aaf.module.elder.guard.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.device.DeviceZoneEnum;
import com.xuejiai.aaf.common.enums.elder.AlertLevelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertStatusEnum;
import com.xuejiai.aaf.common.enums.elder.AlertTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 设备语义事件或后端判定产生的预警。 */
@Getter
@Setter
@Entity
@Table(name = "elder_alert")
public class AlertEvent extends BaseEntity {

    /** 来源设备 ID。 */
    @CrudReference(targetResource = "device.device")
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 长者 ID。 */
    @CrudReference(targetResource = "elder.profile.elder")
    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    /** 设备事件幂等键，后端事件同样生成唯一值。 */
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    /** 预警类型。枚举 {@link AlertTypeEnum}。 */
    @Column(name = "alert_type", nullable = false, length = 32)
    private String alertType;

    /** 预警级别。枚举 {@link AlertLevelEnum}。 */
    @Column(name = "level", nullable = false, length = 16)
    private String level;

    /** 预警状态。枚举 {@link AlertStatusEnum}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = AlertStatusEnum.TRIGGERED.getCode();

    /** 发生区域。枚举 {@link DeviceZoneEnum}。 */
    @Column(name = "zone", nullable = false, length = 32)
    private String zone = DeviceZoneEnum.OTHER.getCode();

    /** 边缘判定置信度。 */
    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    /** 附加语义载荷，不含图片与视频。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    /** 设备侧发生时间。 */
    @Column(name = "device_occurred_at", nullable = false)
    private LocalDateTime deviceOccurredAt;

    /** 服务端接收时间。 */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /** 是否为离线恢复后的晚到事件。 */
    @Column(name = "late", nullable = false)
    private Boolean late = false;

    /** 下一状态动作时间，例如确认窗口到期时间。 */
    @Column(name = "next_action_at")
    private LocalDateTime nextActionAt;

    /** 认领运营用户 ID。 */
    @CrudReference(
            key = "claimedByUser",
            idProperty = "claimedBy",
            targetResource = "system.user",
            viewField = "claimedByUser")
    @Column(name = "claimed_by")
    private Long claimedBy;

    /** 认领时间。 */
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /** 已知晓时间。 */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** 结案时间。 */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
