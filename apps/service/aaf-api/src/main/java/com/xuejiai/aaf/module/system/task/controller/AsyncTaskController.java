package com.xuejiai.aaf.module.system.task.controller;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.messaging.sse.SseSessionManager;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 面向任务提交者的异步任务状态 SSE 接口。 */
@Tag(name = "异步任务")
@RestController
@RequestMapping("/api/async-tasks")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class AsyncTaskController {

    private final OperatorContext operatorContext;
    private final SseSessionManager sseSessionManager;

    @Operation(summary = "订阅自己的异步任务状态事件")
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return sseSessionManager.subscribe(currentOwnerId());
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
    }
}
