package com.xuejiai.aaf.module.ai.assistant.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.SystemAssistantTemplateIds;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantInvocation;
import com.xuejiai.aaf.framework.intelligent.assistant.application.InvocationProfile;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.module.channel.service.WebCustomerServiceBindingResolver.Binding;

import reactor.core.publisher.Flux;

/** 将匿名客服文本转换为固定客服 Assistant 的只读流式命令。 */
@Service
public class CustomerServiceExecutionService {

    private static final String CUSTOMER_SERVICE_ROLE = "system.role.customer-service";

    private final AssistantCommandPort assistants;
    private final AssistantDefinitionPort assistantDefinitions;

    public CustomerServiceExecutionService(
            AssistantCommandPort assistants, AssistantDefinitionPort assistantDefinitions) {
        this.assistants = assistants;
        this.assistantDefinitions = assistantDefinitions;
    }

    public ExecutionStream start(
            Binding binding,
            String visitorSubject,
            String threadId,
            String requestedRunId,
            String input) {
        var tenantId = new TenantId(binding.orgId().toString());
        var assistantId = new AssistantId(SystemAssistantTemplateIds.CUSTOMER_SERVICE);
        var definition =
                assistantDefinitions
                        .findById(tenantId, assistantId)
                        .orElseThrow(() -> new IllegalStateException("系统客服 Assistant 不可用"));
        var role = definition.requireRole(CUSTOMER_SERVICE_ROLE);
        var effectiveRunId =
                stable(
                        "customer-service-run",
                        tenantId.value(),
                        visitorSubject,
                        threadId,
                        requireText(requestedRunId, "runId"));
        var runKey =
                stable(
                        "customer-service-execution",
                        tenantId.value(),
                        visitorSubject,
                        threadId,
                        effectiveRunId);
        var conversationKey =
                stable("customer-service-conversation", tenantId.value(), visitorSubject, threadId);
        var invocationProfile =
                InvocationProfile.primary(
                                null,
                                AssistantInvocation.MemoryMode.DEFAULT,
                                List.of(),
                                ExecutionIntent.conversationalAuto(null))
                        .forAssistantTarget(
                                definition.version().value(),
                                role.key(),
                                null,
                                definition.candidateToolKeys(role));
        var command =
                new AssistantCommand(
                        AssistantCommand.Operation.START,
                        tenantId,
                        new UserId(binding.responsibleOwnerId().toString()),
                        new MemorySubject(tenantId, SubjectKind.VISITOR, visitorSubject),
                        assistantId,
                        new ConversationId(threadId),
                        new SessionId(stable("customer-service-session", runKey)),
                        null,
                        new ExecutionId(stable("customer-service-execution-id", runKey)),
                        new RunId(effectiveRunId),
                        null,
                        new CorrelationId(stable("customer-service-correlation", conversationKey)),
                        null,
                        new IdempotencyKey(stable("customer-service-idempotency", runKey)),
                        ControlMode.READ_ONLY,
                        null,
                        null,
                        0,
                        requireText(input, "input"),
                        CompletionCriteria.responseDelivered(),
                        List.of(),
                        TaskModelSelection.auto(),
                        invocationProfile,
                        Instant.now());
        return new ExecutionStream(effectiveRunId, assistants.execute(command));
    }

    private static String stable(String namespace, String... parts) {
        var canonical = new StringBuilder();
        append(canonical, namespace);
        for (var part : parts) {
            append(canonical, part);
        }
        return UUID.nameUUIDFromBytes(canonical.toString().getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static void append(StringBuilder target, String value) {
        var normalized = value == null ? "" : value;
        target.append(normalized.length()).append(':').append(normalized).append(';');
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }

    public record ExecutionStream(String runId, Flux<ExecutionEvent> events) {}
}
