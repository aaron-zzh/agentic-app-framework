package com.xuejiai.aaf.module.device.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.xuejiai.aaf.common.enums.device.DeviceCameraStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceOnlineStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceTypeEnum;
import com.xuejiai.aaf.common.enums.device.DeviceZoneEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 设备，当前覆盖机器人与固定摄像头节点。 */
@Getter
@Setter
@Entity
@Table(name = "device_info")
@CrudReference(
        key = "createBy",
        idProperty = "createBy",
        targetResource = "system.user",
        viewField = "createBy",
        capabilities = ReferenceCapability.READ)
@CrudReference(
        key = "updateBy",
        idProperty = "updateBy",
        targetResource = "system.user",
        viewField = "updateBy",
        capabilities = ReferenceCapability.READ)
@SQLDelete(
        sql = "UPDATE device_info SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Device extends BaseEntity {

    /** 硬件序列号。 */
    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    /** 系统生成的不透明设备键。 */
    @Column(name = "device_key", nullable = false, length = 100)
    private String deviceKey;

    /** 设备显示名。 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 设备型号。 */
    @Column(name = "model", length = 100)
    private String model;

    /** 固件版本。 */
    @Column(name = "firmware_version", length = 64)
    private String firmwareVersion;

    /** 设备类型。枚举 {@link DeviceTypeEnum}。 */
    @Column(name = "device_type", nullable = false, length = 32)
    private String deviceType;

    /** 部署区域。枚举 {@link DeviceZoneEnum}。 */
    @Column(name = "zone", nullable = false, length = 32)
    private String zone = DeviceZoneEnum.OTHER.getCode();

    /** 设备业务场景稳定键。 */
    @Column(name = "scenario_key", nullable = false, length = 64)
    private String scenarioKey;

    /** 场景主体资源键；无主体的场景可为空。 */
    @Column(name = "subject_type", length = 128)
    private String subjectType;

    /** 场景主体记录 ID；目标资源由 subjectType 动态决定。 */
    @CrudReference(resourceProperty = "subjectType")
    @Column(name = "subject_id")
    private Long subjectId;

    /** 设备级角色覆盖；为空时由所有者默认角色或场景默认角色决定。 */
    @Column(name = "role_key", length = 128)
    private String roleKey;

    /** 设备所有者用户 ID，同时作为计费主体。 */
    @CrudReference(
            key = "ownerUser",
            idProperty = "ownerUserId",
            targetResource = "system.user",
            inputField = "ownerUserId",
            viewField = "ownerUser")
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    /** 设备凭据哈希，永不输出。 */
    @JsonIgnore
    @Column(name = "credential_hash", nullable = false, length = 128)
    private String credentialHash;

    /** 设备凭据展示前缀。 */
    @Column(name = "credential_prefix", nullable = false, length = 32)
    private String credentialPrefix;

    /** 绑定状态。枚举 {@link DeviceStatusEnum}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = DeviceStatusEnum.BOUND.getCode();

    /** 在线状态。枚举 {@link DeviceOnlineStatusEnum}。 */
    @Column(name = "online_status", nullable = false, length = 16)
    private String onlineStatus = DeviceOnlineStatusEnum.OFFLINE.getCode();

    /** 当前启动序号标识。 */
    @Column(name = "boot_id", length = 100)
    private String bootId;

    /** 当前 bootId 的设备启动时间，用于拒绝旧启动延迟消息。 */
    @Column(name = "boot_started_at")
    private LocalDateTime bootStartedAt;

    /** 当前启动内的单调状态序号。 */
    @Column(name = "status_sequence", nullable = false)
    private Long statusSequence = 0L;

    /** 最近心跳时间。 */
    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    /** 最近检测到人员的时间。 */
    @Column(name = "last_person_seen_at")
    private LocalDateTime lastPersonSeenAt;

    /** 摄像头状态。枚举 {@link DeviceCameraStatusEnum}。 */
    @Column(name = "camera_status", nullable = false, length = 16)
    private String cameraStatus = DeviceCameraStatusEnum.UNKNOWN.getCode();

    /** 是否处于隐私模式。 */
    @Column(name = "privacy_mode", nullable = false)
    private Boolean privacyMode = false;

    /** 设备能力与版本快照。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", nullable = false, columnDefinition = "jsonb")
    private String capabilities = "{}";

    /** 凭据撤销时间。 */
    @Column(name = "credential_revoked_at")
    private LocalDateTime credentialRevokedAt;

    /** 凭据最近使用时间。 */
    @Column(name = "credential_last_used_at")
    private LocalDateTime credentialLastUsedAt;
}
