package com.xuejiai.aaf.framework.bizlog.service;

import java.util.List;

import com.xuejiai.aaf.framework.bizlog.beans.LogRecord;

/**
 * 操作日志持久化接口。
 *
 * <p>业务方实现此接口将日志写入数据库/消息队列等存储。 AAF 对接实现见 {@code OperationLogBizLogService}（在 aaf-api 模块）。
 */
public interface ILogRecordService {

    /** 保存操作日志。 */
    void record(LogRecord logRecord);

    /**
     * 按 bizNo 和 type 查询日志（最多 100 条）。
     *
     * @param bizNo 业务标识
     * @param type 日志类型
     */
    List<LogRecord> queryLog(String bizNo, String type);

    /** 按 bizNo、type、subType 查询日志（最多 100 条）。 */
    List<LogRecord> queryLogByBizNo(String bizNo, String type, String subType);
}
