package com.xuejiai.aaf.module.device.vo;

import java.util.Map;

/** 设备启动简报，仅声明通用运行配置与场景扩展数据。 */
public record DeviceBriefVO(
        String scenarioKey,
        CurrentConfig currentConfig,
        SigningPublicKey signingPublicKey,
        Map<String, Object> scenario) {

    public DeviceBriefVO {
        scenario = scenario == null ? Map.of() : Map.copyOf(scenario);
    }

    public record CurrentConfig(
            int heartbeatSeconds, int suspectAfterSeconds, int offlineAfterSeconds) {}

    public record SigningPublicKey(String keyId, String algorithm, String publicKey) {}
}
