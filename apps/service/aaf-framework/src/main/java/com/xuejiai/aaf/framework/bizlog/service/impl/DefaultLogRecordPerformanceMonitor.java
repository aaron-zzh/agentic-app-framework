package com.xuejiai.aaf.framework.bizlog.service.impl;

import org.springframework.util.StopWatch;

import com.xuejiai.aaf.framework.bizlog.service.ILogRecordPerformanceMonitor;

import lombok.extern.slf4j.Slf4j;

/** 性能监控默认实现，debug 级别打印耗时。 */
@Slf4j
public class DefaultLogRecordPerformanceMonitor implements ILogRecordPerformanceMonitor {

    @Override
    public void print(StopWatch stopWatch) {
        log.debug("LogRecord performance={}", stopWatch.prettyPrint());
    }
}
