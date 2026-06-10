package com.xuejiai.aaf.module.system.log.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.bizlog.beans.LogRecord;
import com.xuejiai.aaf.framework.bizlog.service.ILogRecordService;
import com.xuejiai.aaf.framework.logging.OperationLogEvent;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/**
 * ILogRecordService AAF 实现，将 @LogRecord 产生的日志通过 OperationLogEvent 异步持久化。
 *
 * <p>注册为 Spring Bean 后自动覆盖 bizlog 的 DefaultLogRecordServiceImpl。
 */
@Component
@RequiredArgsConstructor
public class AafLogRecordService implements ILogRecordService {

    private final ApplicationEventPublisher eventPublisher;
    private final OperatorContext operatorContext;

    @Override
    public void record(LogRecord logRecord) {
        var createTime =
                logRecord.getCreateTime() != null
                        ? LocalDateTime.ofInstant(logRecord.getCreateTime(), ZoneId.systemDefault())
                        : LocalDateTime.now();
        eventPublisher.publishEvent(
                new OperationLogEvent(
                        operatorContext.currentUserId().orElse(null),
                        logRecord.getOperator(),
                        logRecord.getType(),
                        logRecord.getSubType(),
                        logRecord.getAction(),
                        logRecord.getBizNo(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0L,
                        !logRecord.isFail(),
                        null,
                        createTime));
    }

    @Override
    public List<LogRecord> queryLog(String bizNo, String type) {
        return List.of();
    }

    @Override
    public List<LogRecord> queryLogByBizNo(String bizNo, String type, String subType) {
        return List.of();
    }
}
