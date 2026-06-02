package com.xuejiai.aaf.module.ai.chat.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.chat.domain.ChatTask;
import com.xuejiai.aaf.module.ai.chat.service.ChatTaskScheduler;
import com.xuejiai.aaf.module.ai.chat.service.ChatTaskService;

import lombok.RequiredArgsConstructor;

/**
 * AI 对话任务队列接口——用户在对话中创建/管理任务，助理按队列处理。
 *
 * @author AaronZZH & Kiro
 */
@RestController
@RequestMapping("/api/chat/sessions/{sessionId}/tasks")
@RequiredArgsConstructor
public class ChatTaskController {

    private final ChatTaskService taskService;
    private final ChatTaskScheduler taskScheduler;
    private final OperatorContext operatorContext;

    /** 创建任务 */
    @PostMapping
    public Result<ChatTask> create(@PathVariable Long sessionId, @RequestBody CreateTaskDTO dto) {
        var userId = operatorContext.currentUserId().orElseThrow();
        var task =
                taskService.create(
                        sessionId,
                        userId,
                        dto.title(),
                        dto.description(),
                        dto.priority(),
                        dto.scheduledAt());
        return Result.success(task);
    }

    /** 获取任务列表 */
    @GetMapping
    public Result<List<ChatTask>> list(@PathVariable Long sessionId) {
        return Result.success(taskService.listBySession(sessionId));
    }

    /** 获取下一个待处理任务 */
    @GetMapping("/next")
    public Result<ChatTask> next(@PathVariable Long sessionId) {
        return Result.success(taskService.nextPending(sessionId).orElse(null));
    }

    /** 手动触发执行下一个任务 */
    @PostMapping("/execute-next")
    public Result<Void> executeNext(@PathVariable Long sessionId) {
        taskScheduler.executeNext(sessionId);
        return Result.success();
    }

    /** 更新任务状态 */
    @PatchMapping("/{taskId}/status")
    public Result<ChatTask> updateStatus(
            @PathVariable Long sessionId,
            @PathVariable Long taskId,
            @RequestBody UpdateStatusDTO dto) {
        var task =
                switch (dto.status()) {
                    case "running" -> taskService.start(taskId);
                    case "done" -> taskService.complete(taskId, dto.result());
                    case "failed" -> taskService.fail(taskId, dto.result());
                    case "cancelled" -> taskService.cancel(taskId);
                    default -> throw new IllegalArgumentException("无效状态: " + dto.status());
                };
        return Result.success(task);
    }

    record CreateTaskDTO(
            String title, String description, Integer priority, LocalDateTime scheduledAt) {}

    record UpdateStatusDTO(String status, String result) {}
}
