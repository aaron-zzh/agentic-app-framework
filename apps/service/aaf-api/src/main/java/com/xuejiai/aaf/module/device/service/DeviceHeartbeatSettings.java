package com.xuejiai.aaf.module.device.service;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

/** 设备心跳与离线判定阈值，读取全局系统配置；简报下发与离线扫描共用同一份取值。 */
@Component
public class DeviceHeartbeatSettings {

    static final String HEARTBEAT_SECONDS_KEY = "aaf.device.heartbeat-seconds";
    static final String SUSPECT_AFTER_SECONDS_KEY = "aaf.device.suspect-after-seconds";
    static final String OFFLINE_AFTER_SECONDS_KEY = "aaf.device.offline-after-seconds";

    private static final int DEFAULT_HEARTBEAT_SECONDS = 30;
    private static final int DEFAULT_SUSPECT_AFTER_SECONDS = 60;
    private static final int DEFAULT_OFFLINE_AFTER_SECONDS = 90;

    private final SystemConfigService configService;

    public DeviceHeartbeatSettings(SystemConfigService configService) {
        this.configService = configService;
    }

    public int heartbeatSeconds() {
        return configService.getInteger(HEARTBEAT_SECONDS_KEY, DEFAULT_HEARTBEAT_SECONDS);
    }

    public int suspectAfterSeconds() {
        return configService.getInteger(SUSPECT_AFTER_SECONDS_KEY, DEFAULT_SUSPECT_AFTER_SECONDS);
    }

    public int offlineAfterSeconds() {
        return configService.getInteger(OFFLINE_AFTER_SECONDS_KEY, DEFAULT_OFFLINE_AFTER_SECONDS);
    }
}
