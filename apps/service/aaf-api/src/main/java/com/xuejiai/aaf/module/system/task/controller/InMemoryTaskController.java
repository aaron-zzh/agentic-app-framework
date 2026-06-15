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
 * 内存异步任务进度查询接口（临时方案）。
 *
 * <p>仅服务于 {@link AsyncTaskService} 的内存任务（如批量删除用户等带进度条的操作）。 任务状态存在内存中，服务重启后丢失。
 *
 * <p>TODO: 待 {@code framework/task/queue} 完整支持进度反馈后， 迁移到 {@code TaskRuntime.submitWithProgress()}，此
 * Controller 废弃。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "内存异步任务")
@RestController
@RequestMapping("/api/memory-tasks")
@RequiredArgsConstructor
public class InMemoryTaskController {

    private final AsyncTaskService asyncTaskService;

    @Operation(summary = "查询内存任务进度")
    @GetMapping("/{taskId}/progress")
    public Result<AsyncTask> getProgress(@PathVariable String taskId) {
        var task = asyncTaskService.getProgress(taskId);
        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在");
        }
        return Result.success(task);
    }
}
