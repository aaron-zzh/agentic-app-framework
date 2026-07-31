package com.xuejiai.aaf.module.ai.agui;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantVersion;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionContract;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.*;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

/** AG-UI 唯一入口；无固定 Assistant Bean、无 ThreadLocal、无 legacy fallback。 */
@RestController
@RequestMapping("/api/agui")
@PreAuthorize("isAuthenticated()")
public class AssistantAguiController {
    private final AssistantCommandPort assistants;
    private final OperatorContext operators;

    public AssistantAguiController(AssistantCommandPort assistants, OperatorContext operators) {
        this.assistants = assistants;
        this.operators = operators;
    }

    @PostMapping(
            value = "/run",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestBody RunRequest request) {
        var tenantId = tenant();
        var userId =
                new UserId(
                        operators
                                .currentOwnerId()
                                .map(String::valueOf)
                                .orElseThrow(() -> new AccessDeniedException("请求未认证")));
        var now = Instant.now();
        var command =
                new AssistantCommand(
                        AssistantCommand.Operation.START,
                        tenantId,
                        userId,
                        new MemorySubject(tenantId, SubjectKind.USER, userId.value()),
                        new AssistantId(request.assistantId()),
                        new AssistantVersion(request.assistantVersion()),
                        new ConversationId(request.threadId()),
                        new SessionId(request.threadId()),
                        new TaskId(request.taskId()),
                        new ExecutionId(request.runId()),
                        new RunId(request.runId()),
                        null,
                        new CorrelationId(request.taskId()),
                        null,
                        new IdempotencyKey(request.idempotencyKey()),
                        request.controlMode(),
                        request.executionContract(),
                        null,
                        0,
                        request.input(),
                        CompletionCriteria.responseDelivered(),
                        List.of(),
                        now);
        var emitter = new SseEmitter(600_000L);
        assistants
                .execute(command)
                .subscribe(
                        event -> send(emitter, event),
                        emitter::completeWithError,
                        emitter::complete);
        return emitter;
    }

    private static void send(SseEmitter emitter, ExecutionEvent event) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .id(Long.toString(event.sequence()))
                            .name(event.type().name())
                            .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException failure) {
            emitter.completeWithError(failure);
        }
    }

    private static TenantId tenant() {
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) throw new AccessDeniedException("请求缺少组织上下文");
        return new TenantId(orgId.toString());
    }

    public record RunRequest(
            String assistantId,
            long assistantVersion,
            String threadId,
            String taskId,
            String runId,
            String idempotencyKey,
            ControlMode controlMode,
            ExecutionContract executionContract,
            String input) {}
}
