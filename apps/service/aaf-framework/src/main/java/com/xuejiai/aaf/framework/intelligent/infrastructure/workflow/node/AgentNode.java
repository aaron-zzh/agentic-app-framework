package com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.node;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantCommand;
import com.xuejiai.aaf.framework.intelligent.assistant.application.AssistantInvocation;
import com.xuejiai.aaf.framework.intelligent.assistant.application.InvocationProfile;
import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;
import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantCommandPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventReducer;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 节点——以当前触发用户身份构造单轮任务式 Assistant 调用。
 *
 * <p>未配置 {@code roleKey} 时回退到该用户默认 Assistant 的 {@code defaultRoleKey}（{@code TASK} 交互模式的不可变量要求必须使用
 * {@code FIXED} Route，不存在 {@code AUTO}+{@code TASK} 组合）；{@code skillKey} 可留空，表示"Role
 * 已定、未选技能"的合法状态。工作流节点执行不写入用户长期记忆。
 */
@Slf4j
@Component("agentNode")
@ConditionalOnBean(AssistantCommandPort.class)
@RequiredArgsConstructor
public class AgentNode implements JavaDelegate {

    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(5);

    private final AssistantCommandPort assistants;
    private final AssistantDefinitionPort assistantDefinitions;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            var command = command(execution);
            var events = assistants.execute(command).collectList().block(EXECUTION_TIMEOUT);
            if (events == null || events.isEmpty()) {
                throw new IllegalStateException("Assistant 未返回执行事件");
            }
            var state = ExecutionEventReducer.reduce(events);
            if (!state.terminal()
                    || state.status() != ExecutionEvent.ExecutionEventStatus.COMPLETED) {
                throw new IllegalStateException("Assistant 工作流节点执行未完成: " + state.status());
            }
            execution.setVariable("output", state.resultText());
            execution.setVariable("success", true);
        } catch (RuntimeException failure) {
            log.error("Agent 节点执行失败: nodeId={}", execution.getCurrentActivityId(), failure);
            execution.setVariable("success", false);
            execution.setVariable("error", failure.getMessage());
        }
    }

    private AssistantCommand command(DelegateExecution execution) {
        var input = stringVariable(execution, "input", "");
        var promptOverride = stringVariable(execution, "promptOverride", "");
        var prompt = promptOverride.isBlank() ? input : promptOverride.replace("{{input}}", input);
        var orgId = requiredString(execution, "_aafOrgId");
        var userId = requiredString(execution, "_aafUserId");
        var configuredRoleKey = stringVariable(execution, "roleKey", "");
        var configuredSkillKey = stringVariable(execution, "skillKey", "");

        var tenantId = new TenantId(orgId);
        var userIdValue = new UserId(userId);
        var definition = requireDefaultAssistant(tenantId, userIdValue);
        var roleKey = configuredRoleKey.isBlank() ? definition.defaultRoleKey() : configuredRoleKey;
        var skillKey = configuredSkillKey.isBlank() ? null : configuredSkillKey;

        var unique = UUID.randomUUID().toString();
        var processId = execution.getProcessInstanceId();
        var activityId = execution.getCurrentActivityId();
        var executionId = new ExecutionId("workflow:" + unique);
        var executionIntent =
                ExecutionIntent.taskFixed(
                        roleKey,
                        skillKey,
                        definition.version().value(),
                        ExecutionIntent.ArtifactPolicy.returnOnly(
                                ExecutionIntent.OutputKind.MESSAGE, "text/markdown"),
                        ExecutionIntent.ActionAuthorizationPolicy.requestOnDemand(),
                        null);
        var invocationProfile =
                InvocationProfile.primary(
                        skillKey,
                        AssistantInvocation.MemoryMode.DISABLED,
                        List.of(),
                        executionIntent);

        return new AssistantCommand(
                AssistantCommand.Operation.START,
                tenantId,
                userIdValue,
                new MemorySubject(tenantId, SubjectKind.USER, userId),
                definition.assistantId(),
                new ConversationId("workflow:" + processId),
                new SessionId("workflow:" + processId),
                new TaskId("workflow:" + processId),
                executionId,
                new RunId("workflow:" + unique),
                null,
                new CorrelationId("workflow:" + processId),
                null,
                new IdempotencyKey("workflow:" + processId + ":" + activityId),
                ControlMode.COLLABORATIVE,
                null,
                null,
                0,
                prompt,
                CompletionCriteria.responseDelivered(),
                List.of(),
                TaskModelSelection.auto(),
                invocationProfile,
                Instant.now());
    }

    private AssistantDefinition requireDefaultAssistant(TenantId tenantId, UserId userId) {
        return assistantDefinitions
                .findDefaultForUser(tenantId, userId)
                .orElseThrow(
                        () -> new IllegalStateException("用户默认 Assistant 不存在: " + userId.value()));
    }

    private String requiredString(DelegateExecution execution, String name) {
        var value = stringVariable(execution, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Agent 节点缺少变量: " + name);
        }
        return value;
    }

    private String stringVariable(DelegateExecution execution, String name, String defaultValue) {
        var value = execution.getVariable(name);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
