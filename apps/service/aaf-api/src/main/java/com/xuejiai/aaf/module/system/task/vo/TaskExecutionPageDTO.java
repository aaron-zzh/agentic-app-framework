package com.xuejiai.aaf.module.system.task.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 任务执行记录分页查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskExecutionPageDTO extends PageParam {

    private String taskName;
    private String taskType;
    private String status;
}
