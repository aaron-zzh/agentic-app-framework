package com.xuejiai.aaf.framework.bizlog.service.impl;

import java.util.List;

import com.xuejiai.aaf.framework.bizlog.beans.LogRecord;
import com.xuejiai.aaf.framework.bizlog.service.ILogRecordService;

import lombok.extern.slf4j.Slf4j;

/**
 * ILogRecordService 默认实现（打印 debug 日志）。
 *
 * <p>生产环境应替换为持久化实现（对接 OperationLogEvent 或直接写库）。
 */
@Slf4j
public class DefaultLogRecordServiceImpl implements ILogRecordService {

    @Override
    public void record(LogRecord logRecord) {
        log.info("【bizlog】{}", logRecord);
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
