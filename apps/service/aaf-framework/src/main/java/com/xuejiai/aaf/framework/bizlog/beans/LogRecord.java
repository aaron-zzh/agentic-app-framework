package com.xuejiai.aaf.framework.bizlog.beans;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import lombok.*;

/** 操作日志记录实体，由拦截器组装后传递给 ILogRecordService 持久化。 */
@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class LogRecord {
    /** 日志 ID（持久化后由存储层填充） */
    private Serializable id;

    /** 租户标识 */
    private String tenant;

    /** 操作日志类型，如"订单"、"用户" */
    private String type;

    /** 操作日志子类型，用于区分同类型下的不同场景 */
    private String subType;

    /** 业务标识，如订单号、用户 ID */
    private String bizNo;

    /** 操作人 ID */
    private String operator;

    /** 操作内容（SpEL 渲染后的文案） */
    private String action;

    /** 是否为失败日志 */
    private boolean fail;

    /** 日志创建时间（UTC） */
    private Instant createTime;

    /** 额外扩展信息（JSON 字符串） */
    private String extra;

    /** 代码位置信息（ClassName / MethodName） */
    private Map<CodeVariableType, Object> codeVariable;
}
