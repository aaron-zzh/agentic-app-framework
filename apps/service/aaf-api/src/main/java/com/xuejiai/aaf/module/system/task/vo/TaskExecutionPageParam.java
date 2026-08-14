package com.xuejiai.aaf.module.system.task.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Getter;
import lombok.Setter;

/** 任务执行审计只读查询分页参数。 */
@Getter
@Setter
public class TaskExecutionPageParam extends PageParam {
    private String taskName;
    private String taskType;
    private String status;
    private String bizId;
}
