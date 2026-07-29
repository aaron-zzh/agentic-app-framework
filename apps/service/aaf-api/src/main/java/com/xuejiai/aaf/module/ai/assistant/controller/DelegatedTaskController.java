package com.xuejiai.aaf.module.ai.assistant.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask.Source;
import com.xuejiai.aaf.module.ai.assistant.service.DelegatedTaskEventService;
import com.xuejiai.aaf.module.ai.assistant.service.DelegatedTaskService;
import com.xuejiai.aaf.module.ai.assistant.vo.DelegatedTaskCreateDTO;
import com.xuejiai.aaf.module.ai.assistant.vo.DelegatedTaskEventVO;
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
    private final DelegatedTaskEventService taskEvents;

    public DelegatedTaskController(
            DelegatedTaskService tasks, DelegatedTaskEventService taskEvents) {
        this.tasks = tasks;
        this.taskEvents = taskEvents;
    }

    @Operation(summary = "创建对话或手工委托任务")
    @PostMapping
    public Result<DelegatedTaskVO> create(@Validated @RequestBody DelegatedTaskCreateDTO request) {
        var source =
                switch (request.source()) {
                    case CONVERSATION -> Source.CONVERSATION;
                    case MANUAL -> Source.MANUAL;
                };
        return Result.success(
                tasks.create(
                        source,
                        request.conversationId(),
                        request.title(),
                        request.description(),
                        request.priority()));
    }

    @Operation(summary = "查询当前用户的委托任务")
    @GetMapping("/delegated")
    public Result<List<DelegatedTaskVO>> list(@RequestParam(required = false) String status) {
        return Result.success(tasks.list(status));
    }

    @Operation(summary = "查询委托任务详情")
    @GetMapping("/{taskId}/delegation")
    public Result<DelegatedTaskVO> get(@PathVariable String taskId) {
        return Result.success(tasks.get(taskId));
    }

    @Operation(summary = "查询委托任务执行事件")
    @GetMapping("/{taskId}/events")
    public Result<List<DelegatedTaskEventVO>> events(@PathVariable String taskId) {
        return Result.success(taskEvents.list(taskId));
    }

    @Operation(summary = "订阅委托任务执行事件")
    @GetMapping(value = "/{taskId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String taskId) {
        return taskEvents.subscribe(taskId);
    }

    @Operation(summary = "停止委托任务")
    @PostMapping("/{taskId}/stop")
    public Result<DelegatedTaskVO> stop(
            @PathVariable String taskId, @Validated @RequestBody DelegatedTaskReasonDTO request) {
        return Result.success(tasks.stop(taskId, request.reason()));
    }

    @Operation(summary = "人工接管委托任务")
    @PostMapping("/{taskId}/take-over")
    public Result<DelegatedTaskVO> takeOver(
            @PathVariable String taskId, @Validated @RequestBody DelegatedTaskReasonDTO request) {
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
            @PathVariable String taskId, @Validated @RequestBody DelegatedTaskInputDTO request) {
        return tasks.acceptInput(taskId, request).map(Result::success);
    }
}
