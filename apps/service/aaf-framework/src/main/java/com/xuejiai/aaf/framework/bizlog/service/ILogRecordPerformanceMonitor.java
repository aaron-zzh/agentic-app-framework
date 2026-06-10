package com.xuejiai.aaf.framework.bizlog.service;

import org.springframework.util.StopWatch;

/** 操作日志性能监控接口，默认实现打印 debug 日志。 */
public interface ILogRecordPerformanceMonitor {

    String MONITOR_NAME = "log-record-performance";
    String MONITOR_TASK_BEFORE_EXECUTE = "before-execute";
    String MONITOR_TASK_AFTER_EXECUTE = "after-execute";

    void print(StopWatch stopWatch);
}
