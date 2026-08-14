package com.xuejiai.aaf.module.system.task.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.task.TaskExecution;
import com.xuejiai.aaf.module.system.task.service.TaskExecutionCrudService;
import com.xuejiai.aaf.module.system.task.vo.TaskExecutionPageParam;
import com.xuejiai.aaf.module.system.task.vo.TaskExecutionVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 任务执行审计 Entity Engine 只读查询接口。 */
@Tag(name = "任务执行审计查询")
@RestController
@RequestMapping("/api/task-records/executions")
@RequiredArgsConstructor
public class TaskExecutionEntityController
        extends BaseCrudController<
                TaskExecution, TaskExecutionVO, Void, Void, TaskExecutionPageParam> {

    private final TaskExecutionCrudService taskExecutionCrudService;

    @Override
    protected BaseCrudService<TaskExecution, TaskExecutionVO, Void, Void, TaskExecutionPageParam>
            getService() {
        return taskExecutionCrudService;
    }
}
