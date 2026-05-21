package com.xuejiai.aaf.module.system.task.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.task.ScheduledTaskExecutor;
import com.xuejiai.aaf.framework.task.TaskRegistry;
import com.xuejiai.aaf.module.system.task.domain.TaskExecution;
import com.xuejiai.aaf.module.system.task.repository.TaskExecutionRepository;
import com.xuejiai.aaf.module.system.task.vo.TaskVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 任务管理接口（定时任务 + 异步任务统一管理）。 */
@Tag(name = "任务管理")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskManagementController {

    private final TaskRegistry taskRegistry;
    private final ScheduledTaskExecutor scheduledTaskExecutor;
    private final TaskExecutionRepository taskExecutionRepository;

    @Operation(summary = "任务列表（定时任务）")
    @GetMapping
    public Result<List<TaskVO>> list() {
        var tasks =
                taskRegistry.listAll().stream()
                        .map(
                                d ->
                                        new TaskVO(
                                                d.name(),
                                                d.cronExpression(),
                                                d.enabled(),
                                                d.description()))
                        .toList();
        return Result.success(tasks);
    }

    @Operation(summary = "手动触发任务")
    @PostMapping("/{name}/trigger")
    public Result<Void> trigger(@PathVariable String name) {
        scheduledTaskExecutor.triggerOnce(name);
        return Result.success();
    }

    @Operation(summary = "暂停任务")
    @PostMapping("/{name}/pause")
    public Result<Void> pause(@PathVariable String name) {
        taskRegistry.pause(name);
        scheduledTaskExecutor.cancel(name);
        return Result.success();
    }

    @Operation(summary = "恢复任务")
    @PostMapping("/{name}/resume")
    public Result<Void> resume(@PathVariable String name) {
        taskRegistry.resume(name);
        var def = taskRegistry.get(name);
        if (def != null) {
            scheduledTaskExecutor.schedule(def);
        }
        return Result.success();
    }

    @Operation(summary = "执行记录查询（分页）")
    @GetMapping("/executions")
    public Result<PageResult<TaskExecution>> executions(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        var pageable =
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "startTime"));
        var page =
                (status != null && !status.isBlank())
                        ? taskExecutionRepository.findByStatus(status, pageable)
                        : taskExecutionRepository.findAll(pageable);
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }

    @Operation(summary = "死信队列列表")
    @GetMapping("/dead-letter")
    public Result<PageResult<TaskExecution>> deadLetter(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var pageable =
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "startTime"));
        var page = taskExecutionRepository.findByStatus("failed", pageable);
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }

    @Operation(summary = "死信重试")
    @PostMapping("/dead-letter/{id}/retry")
    public Result<Void> retryDeadLetter(@PathVariable Long id) {
        // 重置状态为 running，由消费者重新处理
        var execution = taskExecutionRepository.findById(id).orElse(null);
        if (execution != null) {
            execution.setStatus("running");
            execution.setRetryCount(execution.getRetryCount() + 1);
            execution.setErrorMessage(null);
            taskExecutionRepository.save(execution);
        }
        return Result.success();
    }
}
