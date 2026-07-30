package com.xuejiai.aaf.module.system.task.controller;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.task.ScheduledTaskExecutor;
import com.xuejiai.aaf.framework.task.TaskExecution;
import com.xuejiai.aaf.framework.task.TaskExecutionRepository;
import com.xuejiai.aaf.framework.task.TaskRegistry;
import com.xuejiai.aaf.framework.task.queue.DeadLetterQueue;
import com.xuejiai.aaf.framework.task.queue.DeadLetterQueue.DeadLetterMessage;
import com.xuejiai.aaf.module.system.task.vo.TaskVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** 任务管理接口（定时任务 + 异步任务统一管理）。 */
@Tag(name = "任务管理")
@Validated
@RestController
@RequestMapping("/api/tasks")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class TaskManagementController {

    private final TaskRegistry taskRegistry;
    private final ScheduledTaskExecutor scheduledTaskExecutor;
    private final TaskExecutionRepository taskExecutionRepository;
    private final DeadLetterQueue deadLetterQueue;

    @Operation(summary = "任务列表（定时任务）")
    @GetMapping
    public Result<List<TaskVO>> list() {
        var tasks =
                taskRegistry.listAll().stream()
                        .map(
                                definition ->
                                        new TaskVO(
                                                definition.name(),
                                                definition.cronExpression(),
                                                definition.enabled(),
                                                definition.description()))
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
        var definition = taskRegistry.get(name);
        if (definition != null) {
            scheduledTaskExecutor.schedule(definition);
        }
        return Result.success();
    }

    @Operation(summary = "执行记录查询（分页）")
    @GetMapping("/executions")
    public Result<PageResult<TaskExecution>> executions(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int pageSize,
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
    public Result<PageResult<DeadLetterMessage>> deadLetter(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int pageSize) {
        var offset = (pageNo - 1) * pageSize;
        return Result.success(
                new PageResult<>(deadLetterQueue.list(offset, pageSize), deadLetterQueue.count()));
    }

    @Operation(summary = "死信重试")
    @PostMapping("/dead-letter/{recordId}/retry")
    public Result<Void> retryDeadLetter(@PathVariable String recordId) {
        if (!deadLetterQueue.retry(recordId)) {
            throw exception(GlobalErrorCode.NOT_FOUND);
        }
        return Result.success();
    }
}
