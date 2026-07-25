package com.xuejiai.aaf.module.ai.assistant.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/** P3 task 事件按全局 eventOffset 断点续读。 */
@RestController
@RequestMapping("/api/ai/tasks")
@RequiredArgsConstructor
public class TaskExecutionEventController {

    private final ExecutionEventStorePort events;
    private final OperatorContext operatorContext;

    @GetMapping(value = "/{taskId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String taskId,
            @RequestHeader(value = "Last-Event-ID", required = false) Long afterOffset) {
        var userId = currentUser();
        var tenantId = currentTenant();
        var emitter = new SseEmitter(60_000L);
        events.readTask(tenantId, new TaskId(taskId), afterOffset == null ? 0 : afterOffset)
                .filter(stored -> stored.event().userId() != null
                        && userId.equals(stored.event().userId().value()))
                .subscribe(
                        stored -> send(emitter, stored),
                        emitter::completeWithError,
                        emitter::complete);
        return emitter;
    }

    private static void send(
            SseEmitter emitter, ExecutionEventStorePort.StoredExecutionEvent stored) {
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(stored.eventOffset()))
                    .name(stored.event().type().name())
                    .data(stored.event(), MediaType.APPLICATION_JSON));
        } catch (IOException failure) {
            emitter.completeWithError(failure);
        }
    }

    private String currentUser() {
        return operatorContext.currentUserId()
                .map(String::valueOf)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private static TenantId currentTenant() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "请求缺少已校验的组织上下文");
        }
        return new TenantId(orgId.toString());
    }
}
