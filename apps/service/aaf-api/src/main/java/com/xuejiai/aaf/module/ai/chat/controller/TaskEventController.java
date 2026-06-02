package com.xuejiai.aaf.module.ai.chat.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.chat.domain.TaskEvent;
import com.xuejiai.aaf.module.ai.chat.service.DurableTaskExecutor;
import com.xuejiai.aaf.module.ai.chat.service.TaskEventStreamService;

import lombok.RequiredArgsConstructor;

/** 任务执行可观测接口——事件日志查询 + SSE 实时推送。 */
@RestController
@RequestMapping("/api/chat/tasks")
@RequiredArgsConstructor
public class TaskEventController {

    private final DurableTaskExecutor durableExecutor;
    private final TaskEventStreamService eventStreamService;

    /** 获取任务的完整事件日志 */
    @GetMapping("/{taskId}/events")
    public Result<List<TaskEvent>> getEvents(@PathVariable Long taskId) {
        return Result.success(durableExecutor.getEvents(taskId));
    }

    /** SSE 实时订阅任务执行事件 */
    @GetMapping("/{taskId}/events/stream")
    public SseEmitter streamEvents(@PathVariable Long taskId) {
        return eventStreamService.subscribe(taskId);
    }
}
