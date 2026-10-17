package com.xuejiai.aaf.module.device.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.xuejiai.aaf.common.constant.DictType;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.crud.export.DictFormat;

import io.swagger.v3.oas.annotations.media.Schema;

/** 设备响应。明文凭据只通过绑定后的配对轮询返回一次。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "设备")
public record DeviceVO(
        Long id,
        Integer version,
        String serialNumber,
        String deviceKey,
        String name,
        String model,
        String firmwareVersion,
        @DictFormat(DictType.Device.DEVICE_TYPE) String deviceType,
        @DictFormat(DictType.Device.DEVICE_ZONE) String zone,
        @DictFormat(DictType.Device.SCENARIO) String scenarioKey,
        String subjectType,
        Long subjectId,
        String roleKey,
        Long ownerUserId,
        String credentialPrefix,
        @DictFormat(DictType.Device.DEVICE_STATUS) String status,
        @DictFormat(DictType.Device.DEVICE_ONLINE_STATUS) String onlineStatus,
        String bootId,
        LocalDateTime lastHeartbeatAt,
        LocalDateTime lastPersonSeenAt,
        @DictFormat(DictType.Device.DEVICE_CAMERA_STATUS) String cameraStatus,
        Boolean privacyMode,
        String capabilities,
        LocalDateTime credentialRevokedAt,
        LocalDateTime credentialLastUsedAt,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ResourceRefDTO subject,
        ResourceRefDTO ownerUser,
        ResourceRefDTO createBy,
        ResourceRefDTO updateBy) {}
