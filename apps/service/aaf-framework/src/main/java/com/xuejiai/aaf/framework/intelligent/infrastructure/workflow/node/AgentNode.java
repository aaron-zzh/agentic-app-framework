package com.xuejiai.aaf.framework.intelligent.infrastructure.workflow.node;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext.ToolAuthorizationRule;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AgentId;
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

/** Agent 节点——通过 AgentExecutionPort 执行已发布的 AgentScope Agent。 */
@Slf4j
@Component("agentNode")
@ConditionalOnBean(AgentExecutionPort.class)
@RequiredArgsConstructor
public class AgentNode implements JavaDelegate {

    private final AgentExecutionPort agentExecutionPort;
    private final ToolRegistry toolRegistry;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            var command = command(execution);
            var events =
                    agentExecutionPort.execute(command).collectList().block(Duration.ofMinutes(5));
            if (events == null) {
                throw new IllegalStateException("Agent 未返回执行事件");
            }
            var failure =
                    events.stream()
                            .filter(ExecutionEvent::isTerminal)
                            .filter(
                                    event ->
                                            event.status()
                                                    == ExecutionEvent.ExecutionEventStatus.FAILED)
                            .findFirst();
            if (failure.isPresent()) {
                throw new IllegalStateException(
                        String.valueOf(
                                failure.get()
                                        .payload()
                                        .values()
                                        .getOrDefault("error", "Agent 执行失败")));
            }
            var output =
                    events.stream()
                            .filter(event -> event.type() == ExecutionEventType.MESSAGE_COMPLETED)
                            .map(event -> event.payload().values().get("text"))
                            .filter(java.util.Objects::nonNull)
                            .map(String::valueOf)
                            .reduce((left, right) -> right)
                            .orElse("");
            execution.setVariable("output", output);
            execution.setVariable("success", true);
        } catch (RuntimeException failure) {
            log.error("Agent 节点执行失败: nodeId={}", execution.getCurrentActivityId(), failure);
            execution.setVariable("success", false);
            execution.setVariable("error", failure.getMessage());
        }
    }

    private AgentExecutionCommand command(DelegateExecution execution) {
        var configuredAgentId = requiredString(execution, "agentId");
        var input = stringVariable(execution, "input", "");
        var promptOverride = stringVariable(execution, "promptOverride", "");
        var prompt = promptOverride.isBlank() ? input : promptOverride.replace("{{input}}", input);
        var version = intVariable(execution, "agentVersion", 1);
        var orgId = requiredString(execution, "_aafOrgId");
        var userId = requiredString(execution, "_aafUserId");

        var unique = UUID.randomUUID().toString();
        var processId = execution.getProcessInstanceId();
        var activityId = execution.getCurrentActivityId();
        var tools = configuredTools(execution);
        var context =
                new InvocationContext(
                        new TenantId(orgId),
                        new UserId(userId),
                        null,
                        new ConversationId("workflow:" + processId),
                        new SessionId("workflow:" + processId),
                        new TaskId("workflow:" + processId),
                        new ExecutionId("workflow:" + unique),
                        new RunId("workflow:" + unique),
                        null,
                        new CorrelationId("workflow:" + processId),
                        null,
                        new IdempotencyKey("workflow:" + processId + ":" + activityId),
                        ControlMode.COLLABORATIVE,
                        null,
                        null,
                        toolAuthorization(tools));
        return new AgentExecutionCommand(
                new SubagentSpec.Predefined(new AgentId(configuredAgentId), version),
                Optional.empty(),
                "",
                tools,
                0,
                List.of(new AgentMessage("workflow:" + unique, AgentMessage.Role.USER, prompt)),
                context);
    }

    private ToolAuthorizationContext toolAuthorization(Set<String> tools) {
        var metadata =
                toolRegistry.listAll().stream()
                        .filter(tool -> tools.contains(tool.name()))
                        .collect(
                                Collectors.toMap(
                                        ToolRegistry.ToolMeta::name,
                                        tool ->
                                                new ToolAuthorizationRule(
                                                        tool.readOnly(),
                                                        !tool.readOnly(),
                                                        !tool.readOnly())));
        return new ToolAuthorizationContext(Map.copyOf(metadata));
    }

    private Set<String> configuredTools(DelegateExecution execution) {
        var value = stringVariable(execution, "tools", "");
        if (value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
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

    private int intVariable(DelegateExecution execution, String name, int defaultValue) {
        var value = execution.getVariable(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
    }
}
