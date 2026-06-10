package com.xuejiai.aaf.framework.bizlog.beans;

import lombok.Builder;
import lombok.Data;

/** 从 @LogRecord 注解解析出的操作配置。 */
@Data
@Builder
public class LogRecordOps {
    private String successLogTemplate;
    private String failLogTemplate;
    private String operatorId;
    private String type;
    private String bizNo;
    private String subType;
    private String extra;
    private String condition;
    private String isSuccess;
}
