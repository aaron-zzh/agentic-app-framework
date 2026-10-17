package com.xuejiai.aaf.module.device.api;

import java.time.LocalDateTime;
import java.util.List;

/** 设备只读查询入口。场景包通过本接口读取自己场景下的设备，不直接访问设备实体与仓储。 */
public interface DeviceQueryApi {

    /** 加行锁读取设备，需在事务内调用；设备不存在时抛出业务异常。 */
    DeviceView requireLocked(Long deviceId);

    /** 某场景主体名下的全部设备，按 ID 升序。 */
    List<DeviceView> listBySubject(String scenarioKey, Long subjectId);

    /** 某场景下已绑定且在线的全部设备。 */
    List<DeviceView> listBoundOnline(String scenarioKey);

    /** 设备只读视图。 */
    record DeviceView(
            Long id,
            String deviceKey,
            String name,
            String deviceType,
            String zone,
            String scenarioKey,
            Long subjectId,
            Long ownerUserId,
            Long orgId,
            Long workspaceId,
            String status,
            String onlineStatus,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime lastPersonSeenAt,
            String cameraStatus,
            boolean privacyMode) {}
}
