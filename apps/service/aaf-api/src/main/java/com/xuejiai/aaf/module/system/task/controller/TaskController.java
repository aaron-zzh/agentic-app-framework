package com.xuejiai.aaf.module.system.task.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.task.async.AsyncTask;
import com.xuejiai.aaf.module.system.task.async.AsyncTaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 异步任务进度查询接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "异步任务")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final AsyncTaskService asyncTaskService;

    @Operation(summary = "查询任务进度")
    @GetMapping("/{taskId}/progress")
    public Result<AsyncTask> getProgress(@PathVariable String taskId) {
        var task = asyncTaskService.getProgress(taskId);
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在");
        }
        return Result.success(task);
    }
}
