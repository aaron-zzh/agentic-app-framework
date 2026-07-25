package com.xuejiai.aaf.module.ai.assistant.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.assistant.service.DelegatedTaskService;
import com.xuejiai.aaf.module.ai.assistant.vo.DelegatedTaskInputDTO;
import com.xuejiai.aaf.module.ai.assistant.vo.DelegatedTaskReasonDTO;
import com.xuejiai.aaf.module.ai.assistant.vo.DelegatedTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "委托任务中心")
@RestController
@RequestMapping("/api/ai/tasks")
@PreAuthorize("isAuthenticated()")
public class DelegatedTaskController {
    private final DelegatedTaskService tasks;

    public DelegatedTaskController(DelegatedTaskService tasks) {
        this.tasks = tasks;
    }

    @Operation(summary = "查询当前用户的委托任务")
    @GetMapping("/delegated")
    public Result<List<DelegatedTaskVO>> list(
            @RequestParam(required = false) String status) {
        return Result.success(tasks.list(status));
    }

    @Operation(summary = "查询委托任务详情")
    @GetMapping("/{taskId}/delegation")
    public Result<DelegatedTaskVO> get(@PathVariable String taskId) {
        return Result.success(tasks.get(taskId));
    }

    @Operation(summary = "停止委托任务")
    @PostMapping("/{taskId}/stop")
    public Result<DelegatedTaskVO> stop(
            @PathVariable String taskId,
            @Validated @RequestBody DelegatedTaskReasonDTO request) {
        return Result.success(tasks.stop(taskId, request.reason()));
    }

    @Operation(summary = "人工接管委托任务")
    @PostMapping("/{taskId}/take-over")
    public Result<DelegatedTaskVO> takeOver(
            @PathVariable String taskId,
            @Validated @RequestBody DelegatedTaskReasonDTO request) {
        return Result.success(tasks.takeOver(taskId, request.reason()));
    }

    @Operation(summary = "将委托任务交回 Agent")
    @PostMapping("/{taskId}/hand-back")
    public Result<DelegatedTaskVO> handBack(@PathVariable String taskId) {
        return Result.success(tasks.handBack(taskId));
    }

    @Operation(summary = "提交任务运行期输入")
    @PostMapping("/{taskId}/inputs")
    public Mono<Result<DelegatedTaskVO>> input(
            @PathVariable String taskId,
            @Validated @RequestBody DelegatedTaskInputDTO request) {
        return tasks.acceptInput(taskId, request).map(Result::success);
    }
}
