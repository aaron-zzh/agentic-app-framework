package com.xuejiai.aaf.module.device.scenario;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_SCENARIO_DISABLED;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;

/** 设备场景启用门。配置值按声明顺序决定绑定时的默认场景。 */
@Service
public class DeviceScenarioAccess {

    public static final String ENABLED_SCENARIOS_KEY = "device.scenarios.enabled";

    private final SystemConfigService configService;
    private final DeviceScenarioRegistry registry;

    public DeviceScenarioAccess(
            SystemConfigService configService, DeviceScenarioRegistry registry) {
        this.configService = configService;
        this.registry = registry;
    }

    public DeviceScenario requireEnabled(String scenarioKey) {
        var scenario = registry.require(scenarioKey);
        if (!enabledKeys().contains(scenario.key())) {
            throw exception(DEVICE_SCENARIO_DISABLED, scenario.key());
        }
        return scenario;
    }

    public DeviceScenario firstEnabled() {
        var first = enabledKeys().stream().findFirst();
        if (first.isEmpty()) {
            throw exception(DEVICE_SCENARIO_DISABLED, "");
        }
        return registry.require(first.orElseThrow());
    }

    public List<String> enabledKeys() {
        var configured = configService.getString(ENABLED_SCENARIOS_KEY, "");
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
