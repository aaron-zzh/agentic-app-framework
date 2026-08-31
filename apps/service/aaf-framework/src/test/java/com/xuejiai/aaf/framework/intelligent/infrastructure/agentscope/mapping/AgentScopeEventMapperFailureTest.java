package com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.agent.model.ActivatedSkill;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentExecutionCommand;
import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.agent.model.AuthorizedSkillSummary;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptLayerSource;
import com.xuejiai.aaf.framework.intelligent.agent.model.CompiledSystemPrompt.PromptSourceKind;
import com.xuejiai.aaf.framework.intelligent.agent.model.ExecutionPolicy;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.model.ModelSelectionRequirement;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillExecutionProfile;
import com.xuejiai.aaf.framework.intelligent.agent.model.SkillSelectionManifest;
import com.xuejiai.aaf.framework.intelligent.agent.model.SubagentSpec;
import com.xuejiai.aaf.framework.intelligent.agent.model.ToolAuthorizationContext;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillScope;
import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillSelectionMode;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort.Lease;
import com.xuejiai.aaf.framework.intelligent.core.skill.SkillVersionRef;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.mapping.AgentScopeEventMapper.MappingState;
import com.xuejiai.aaf.framework.intelligent.infrastructure.agentscope.tool.ToolResultEvidenceStore;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CausationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

/** RQ-04：失败事件必须携带稳定的失败类别、可重试标记与日志关联键，不再只给异常类简单名。 */
class AgentScopeEventMapperFailureTest {

    private final AgentScopeEventMapper mapper =
            new AgentScopeEventMapper(new ToolResultEvidenceStore());

    @Test
    @DisplayName("Given 时限类异常 When 映射失败事件 Then 载荷带 TIMEOUT 类别且标记可重试")
    void should_expose_stable_failure_contract_for_timeout() {
        var event =
                mapper.failure(
                        command(),
                        "agent.failure-test",
                        new MappingState(0),
                        new TimeoutException());

        assertThat(event.type()).isEqualTo(ExecutionEventType.RUN_FAILED);
        assertThat(event.status()).isEqualTo(ExecutionEventStatus.FAILED);
        assertThat(event.payload().values())
                .containsEntry("failureCategory", "TIMEOUT")
                .containsEntry("retryable", true)
                .containsEntry("errorType", "TimeoutException");
        assertThat(event.payload().values().get("failureId")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("Given 无法归类异常 When 映射失败事件 Then 标记为不可自动重试")
    void should_mark_unknown_failure_as_not_retryable() {
        var event =
                mapper.failure(
                        command(),
                        "agent.failure-test",
                        new MappingState(0),
                        new RuntimeException("未知故障"));

        assertThat(event.payload().values())
                .containsEntry("failureCategory", "UNKNOWN")
                .containsEntry("retryable", false);
    }

    private static AgentExecutionCommand command() {
        var dynamic =
                new SubagentSpec.Dynamic(
                        "agent.failure-test",
                        "验证失败契约",
                        "只执行测试任务",
                        List.of(),
                        ExecutionPolicy.withDefaultTimeouts(
                                3, 1, java.time.Duration.ofSeconds(30), 128000),
                        ModelSelectionRequirement.balanced(),
                        false);
        return new AgentExecutionCommand(
                dynamic,
                Optional.empty(),
                AgentExecutionCommand.ExecutionMode.DIRECT,
                Optional.empty(),
                skillExecutionProfile(),
                compiled(dynamic.identifier()),
                0,
                List.of(new AgentMessage("message-1", AgentMessage.Role.USER, "执行")),
                context());
    }

    private static SkillExecutionProfile skillExecutionProfile() {
        var selection =
                new SkillSelectionManifest(
                        "test.route",
                        "test.skill",
                        SkillSelectionMode.FIXED,
                        1,
                        List.of(
                                new AuthorizedSkillSummary(
                                        "test.skill",
                                        "测试技能",
                                        "验证失败契约",
                                        SkillScope.SYSTEM,
                                        SkillActivationMode.ON_DEMAND,
                                        List.of(),
                                        Set.of(),
                                        Set.of())));
        return new SkillExecutionProfile(
                selection,
                List.of(
                        new ActivatedSkill(
                                "test.skill",
                                new SkillVersionRef(1L, 1L, 1),
                                SkillScope.SYSTEM,
                                SkillActivationMode.ON_DEMAND,
                                "技能提示",
                                Set.of(),
                                Set.of(),
                                List.of(),
                                List.of(),
                                false)),
                List.of());
    }

    private static CompiledSystemPrompt compiled(String identity) {
        return CompiledSystemPrompt.compile(
                List.of(
                        new PromptLayerSource(
                                PromptLayerKind.CONSTITUTION,
                                PromptSourceKind.ENGINE_TEMPLATE,
                                CompiledSystemPrompt.CONSTITUTION_NAME,
                                "1",
                                "测试 Constitution"),
                        new PromptLayerSource(
                                PromptLayerKind.IDENTITY,
                                PromptSourceKind.AAF_POLICY,
                                identity,
                                "1",
                                "测试执行身份"),
                        new PromptLayerSource(
                                PromptLayerKind.INVOCATION_POLICY,
                                PromptSourceKind.AAF_POLICY,
                                "invocation:test",
                                "1",
                                "测试调用策略"),
                        new PromptLayerSource(
                                PromptLayerKind.PERSONA,
                                PromptSourceKind.ASSISTANT_PERSONA,
                                "persona:test",
                                "1",
                                "测试 Persona")));
    }

    private static InvocationContext context() {
        return new InvocationContext(
                new TenantId("tenant-1"),
                new UserId("user-1"),
                null,
                new AssistantId("assistant-1"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                new TaskId("task-1"),
                new ExecutionId("execution-1"),
                new RunId("run-1"),
                null,
                new CorrelationId("correlation-1"),
                new CausationId("causation-1"),
                new IdempotencyKey("idempotency-1"),
                ControlMode.READ_ONLY,
                null,
                (Lease) null,
                new ToolAuthorizationContext(Map.of()));
    }
}
