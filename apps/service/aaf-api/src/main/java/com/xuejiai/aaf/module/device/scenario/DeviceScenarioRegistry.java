package com.xuejiai.aaf.module.device.scenario;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_SCENARIO_NOT_FOUND;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** 收集全部设备场景实现，并按稳定 key 提供查询。 */
@Component
public class DeviceScenarioRegistry {

    private final Map<String, DeviceScenario> scenarios;

    public DeviceScenarioRegistry(List<DeviceScenario> scenarios) {
        var indexed = new LinkedHashMap<String, DeviceScenario>();
        for (var scenario : scenarios) {
            var key = normalize(scenario.key());
            if (key.isEmpty()) {
                throw new IllegalStateException("设备场景 key 不能为空");
            }
            if (indexed.putIfAbsent(key, scenario) != null) {
                throw new IllegalStateException("设备场景 key 重复: " + key);
            }
        }
        this.scenarios = Map.copyOf(indexed);
    }

    public Optional<DeviceScenario> find(String key) {
        return Optional.ofNullable(scenarios.get(normalize(key)));
    }

    public DeviceScenario require(String key) {
        return find(key).orElseThrow(() -> exception(DEVICE_SCENARIO_NOT_FOUND, key));
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim();
    }
}
