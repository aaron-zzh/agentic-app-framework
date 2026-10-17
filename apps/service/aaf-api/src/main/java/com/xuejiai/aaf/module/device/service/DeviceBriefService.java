package com.xuejiai.aaf.module.device.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.module.device.mqtt.DeviceCommandSigningService;
import com.xuejiai.aaf.module.device.scenario.DeviceScenario;
import com.xuejiai.aaf.module.device.scenario.DeviceScenarioAccess;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;
import com.xuejiai.aaf.module.device.vo.DeviceBriefVO;

/** 设备启动简报组装服务。通用配置与场景扩展数据保持分区。 */
@Service
@Transactional(readOnly = true)
public class DeviceBriefService {

    private final DeviceHeartbeatSettings heartbeatSettings;
    private final DeviceCommandSigningService signingService;
    private final DeviceScenarioAccess scenarioAccess;
    private final DeviceOwnerExecutionService ownerExecutionService;

    public DeviceBriefService(
            DeviceHeartbeatSettings heartbeatSettings,
            DeviceCommandSigningService signingService,
            DeviceScenarioAccess scenarioAccess,
            DeviceOwnerExecutionService ownerExecutionService) {
        this.heartbeatSettings = heartbeatSettings;
        this.signingService = signingService;
        this.scenarioAccess = scenarioAccess;
        this.ownerExecutionService = ownerExecutionService;
    }

    public DeviceBriefVO getBrief(DevicePrincipal principal) {
        var scenario = scenarioAccess.requireEnabled(principal.scenarioKey());
        return ownerExecutionService.runAsOwner(
                principal,
                "device-brief",
                () ->
                        OrgContext.runIfAbsent(
                                principal.orgId(),
                                principal.workspaceId(),
                                () -> buildBrief(principal, scenario)));
    }

    private DeviceBriefVO buildBrief(DevicePrincipal principal, DeviceScenario scenario) {
        var publicKey = signingService.publicKey();
        return new DeviceBriefVO(
                scenario.key(),
                currentConfig(),
                new DeviceBriefVO.SigningPublicKey(
                        publicKey.keyId(), publicKey.algorithm(), publicKey.publicKey()),
                scenario.briefExtras(principal));
    }

    private DeviceBriefVO.CurrentConfig currentConfig() {
        return new DeviceBriefVO.CurrentConfig(
                heartbeatSettings.heartbeatSeconds(),
                heartbeatSettings.suspectAfterSeconds(),
                heartbeatSettings.offlineAfterSeconds());
    }
}
