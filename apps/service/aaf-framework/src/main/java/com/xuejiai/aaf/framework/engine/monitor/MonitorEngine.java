package com.xuejiai.aaf.framework.engine.monitor;

import java.util.Map;

/**
 * 监控引擎——系统运行状态监控、告警、指标采集。
 *
 * <p>职责：Agent 执行监控、模型调用监控、资源使用监控、异常告警。
 * 当前 framework/logging/ 中已有基础实现（health/metrics），本接口定义统一监控入口。
 * v0.2+ 完善。
 */
public interface MonitorEngine {

    /** 上报指标。 */
    void reportMetric(String name, double value, Map<String, String> tags);

    /** 上报告警。 */
    void alert(AlertLevel level, String title, String message);

    /** 查询系统健康状态。 */
    HealthStatus health();

    /** 告警级别 */
    enum AlertLevel { INFO, WARNING, CRITICAL }

    /** 健康状态 */
    record HealthStatus(boolean healthy, Map<String, ComponentStatus> components) {}

    /** 组件状态 */
    record ComponentStatus(boolean up, String detail) {}
}
