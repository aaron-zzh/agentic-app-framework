package com.xuejiai.aaf.module.system.task.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.task.AsyncTaskStatus;

import lombok.Getter;
import lombok.Setter;

/** 异步任务只读查询分页参数。 */
@Getter
@Setter
public class AsyncTaskPageParam extends PageParam {
    private String taskId;
    private String taskType;
    private AsyncTaskStatus status;
}
