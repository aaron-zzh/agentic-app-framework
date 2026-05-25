package com.xuejiai.aaf.framework.engine.monitor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认监控引擎实现——日志级监控。
 *
 * <p>后续可桥接 Micrometer/Prometheus，当前仅日志输出。
 */
@Slf4j
@Component
public class DefaultMonitorEngine implements MonitorEngine {

    @Override
    public void reportMetric(String name, double value, Map<String, String> tags) {
        log.debug("指标上报: {}={} tags={}", name, value, tags);
    }

    @Override
    public void alert(AlertLevel level, String title, String message) {
        switch (level) {
            case CRITICAL -> log.error("[告警-严重] {}: {}", title, message);
            case WARNING -> log.warn("[告警-警告] {}: {}", title, message);
            case INFO -> log.info("[告警-信息] {}: {}", title, message);
        }
    }

    @Override
    public HealthStatus health() {
        var components = new LinkedHashMap<String, ComponentStatus>();
        components.put("system", new ComponentStatus(true, "UP"));
        return new HealthStatus(true, components);
    }
}
