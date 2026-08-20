package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.FixedSkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.AgentExecutionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.SkillCatalogPort;
import com.xuejiai.aaf.framework.intelligent.assistant.application.PromptAssembler;
import com.xuejiai.aaf.framework.intelligent.assistant.model.InvocationPolicy;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
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
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcAgentExecutionPort;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Result;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Submission;

import lombok.RequiredArgsConstructor;

/** 将 AIGC Agent 动作提交到 framework AgentExecutionPort。 */
@Component
@RequiredArgsConstructor
public class FrameworkAigcAgentExecutionAdapter implements AigcAgentExecutionPort {

    private final AgentExecutionPort agentExecutionPort;
    private final AgentDefinitionPort agentDefinitions;
    private final SkillCatalogPort skillCatalog;
    private final PromptAssembler promptAssembler;

    @Override
    public Submission submit(Command command) {
        var target = parseTarget(command.targetRef());
        var identity = "aigc:" + command.executionRunId();
        var executionId = new ExecutionId(identity);
        var runId = new RunId(identity);
        var agentId = new AgentId(target.agentId());
        var agentSpec =
                agentDefinitions
                        .findByIdAndVersion(agentId, target.version())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Agent 定义不存在: "
                                                        + target.agentId()
                                                        + "@"
                                                        + target.version()));
        var skillExecutionProfile =
                FixedSkillExecutionProfile.from(requireSystemSkill(), List.of());
        var compiledSystemPrompt =
                promptAssembler.compileAgent(
                        agentSpec,
                        executionId.value(),
                        skillExecutionProfile,
                        InvocationPolicy.AIGC);
        var invocation =
                new InvocationContext(
                        new TenantId(command.orgId().toString()),
                        new UserId(command.userId().toString()),
                        command.workspaceId(),
                        null,
                        new ConversationId("aigc-project:" + command.projectId()),
                        new SessionId(identity),
                        new TaskId(identity),
                        executionId,
                        runId,
                        null,
                        new CorrelationId("aigc-project:" + command.projectId()),
                        null,
                        new IdempotencyKey(command.idempotencyKey()),
                        ControlMode.COLLABORATIVE,
                        null,
                        null,
                        new ToolAuthorizationContext(Map.of()));
        var runtimeCommand =
                new AgentExecutionCommand(
                        new SubagentSpec.Predefined(agentId, target.version()),
                        Optional.empty(),
                        AgentExecutionCommand.ExecutionMode.DIRECT,
                        Optional.empty(),
                        skillExecutionProfile,
                        compiledSystemPrompt,
                        0,
                        List.of(
                                new AgentMessage(
                                        identity, AgentMessage.Role.USER, command.prompt())),
                        invocation);
        var completion =
                agentExecutionPort
                        .execute(runtimeCommand)
                        .collectList()
                        .map(this::toResult)
                        .toFuture();
        return new Submission(executionId.value(), runId.value(), completion);
    }

    private com.xuejiai.aaf.framework.intelligent.core.skill.SkillDef requireSystemSkill() {
        return skillCatalog
                .findByCode("builtin-agent-execution")
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "系统内建 Skill 未初始化: builtin-agent-execution"));
    }

    private Result toResult(List<ExecutionEvent> events) {
        var terminal =
                events.stream()
                        .filter(ExecutionEvent::isTerminal)
                        .reduce((first, second) -> second)
                        .orElseThrow(() -> new IllegalStateException("Agent runtime 未返回终态事件"));
        if (terminal.status() != ExecutionEventStatus.COMPLETED) {
            throw new IllegalStateException(failureMessage(terminal));
        }
        var output =
                events.stream()
                        .filter(event -> event.type() == ExecutionEventType.MESSAGE_COMPLETED)
                        .map(event -> event.payload().values().get("text"))
                        .filter(java.util.Objects::nonNull)
                        .map(Object::toString)
                        .reduce((first, second) -> second)
                        .orElse("");
        return new Result(
                output,
                Map.of(
                        "runtimeType", "agent",
                        "terminalEventType", terminal.type().name(),
                        "eventCount", events.size()));
    }

    private String failureMessage(ExecutionEvent terminal) {
        var values = terminal.payload().values();
        for (var key : List.of("error", "reason", "message")) {
            if (values.get(key) != null) {
                return String.valueOf(values.get(key));
            }
        }
        return "Agent runtime 执行失败: " + terminal.status();
    }

    private AgentTarget parseTarget(String targetRef) {
        var separator = targetRef.lastIndexOf('@');
        if (separator <= 0 || separator == targetRef.length() - 1) {
            throw new IllegalArgumentException("Agent targetRef 必须为 agentId@version");
        }
        try {
            return new AgentTarget(
                    targetRef.substring(0, separator),
                    Long.parseLong(targetRef.substring(separator + 1)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Agent targetRef 必须为 agentId@version", exception);
        }
    }

    private record AgentTarget(String agentId, long version) {

        private AgentTarget {
            if (agentId == null || agentId.isBlank() || version < 1) {
                throw new IllegalArgumentException("Agent targetRef 必须包含有效 agentId 和版本");
            }
        }
    }
}
