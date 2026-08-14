package com.xuejiai.aaf.module.system.task.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.task.AsyncTask;
import com.xuejiai.aaf.module.system.task.service.AsyncTaskCrudService;
import com.xuejiai.aaf.module.system.task.vo.AsyncTaskPageParam;
import com.xuejiai.aaf.module.system.task.vo.AsyncTaskVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 异步任务 Entity Engine 只读查询接口。 */
@Tag(name = "异步任务查询")
@RestController
@RequestMapping("/api/task-records/async")
@RequiredArgsConstructor
public class AsyncTaskEntityController
        extends BaseCrudController<AsyncTask, AsyncTaskVO, Void, Void, AsyncTaskPageParam> {

    private final AsyncTaskCrudService asyncTaskCrudService;

    @Override
    protected BaseCrudService<AsyncTask, AsyncTaskVO, Void, Void, AsyncTaskPageParam> getService() {
        return asyncTaskCrudService;
    }
}
