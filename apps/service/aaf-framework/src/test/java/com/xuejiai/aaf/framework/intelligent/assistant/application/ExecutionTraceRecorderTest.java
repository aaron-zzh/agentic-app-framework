package com.xuejiai.aaf.framework.intelligent.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.intelligent.assistant.model.CompletionCriteria;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskModelSelection;
import com.xuejiai.aaf.framework.intelligent.cognition.application.MemoryGovernanceService;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryGovernancePort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryWritePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.OwnerType;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventPayload;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ConversationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.CorrelationId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.EventId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.IdempotencyKey;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.RunId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.SessionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;

class ExecutionTraceRecorderTest {

    private static final Instant T0 = Instant.parse("2026-11-08T08:00:00Z");

    private final AtomicLong sequence = new AtomicLong();

    @Test
    @DisplayName("Given DIRECT 执行一次模型调用后完成 When 生成摘要 Then 一个分组块包含角色、技能、工具、上下文、模型与学习")
    void should_render_grouped_summary_when_direct_execution_completed() {
        // 准备参数
        var trace = directTrace();
        trace.profile(
                "COORDINATOR",
                "system.role.generalist",
                "通才",
                List.of(),
                null,
                List.of("webSearch", "knowledge_search", "promote_to_task"),
                "2");
        trace.context(2, 1, 0, 1930, 128_000);

        // 调用
        trace.observe(event(ExecutionEventType.MODEL_CALL_STARTED, 0, Map.of()));
        trace.observe(event(ExecutionEventType.MESSAGE_DELTA, 19_100, Map.of("delta", "你")));
        trace.observe(
                event(
                        ExecutionEventType.MODEL_CALL_COMPLETED,
                        28_800,
                        Map.of("modelId", "2", "inputTokens", 1930, "outputTokens", 210)));
        trace.validation("COMPLETED", "完成条件已满足");
        trace.observe(
                event(
                        ExecutionEventType.EXECUTION_COMPLETED,
                        29_000,
                        Map.of("turnEndReason", "USER_TURN_END")));
        trace.sessionTurn("已写入");
        trace.longTermMemoryLearned(1, 2);
        var summary = trace.render("ON_COMPLETE", 33_100);

        // 断言
        assertThat(summary.lines().toList())
                .first()
                .isEqualTo(
                        "[执行轨迹] executionId=exec-1 模式=DIRECT 结果=COMPLETED（USER_TURN_END） 耗时=33.1s");
        assertThat(summary)
                .contains("├ 角色    system.role.generalist（通才，COORDINATOR）")
                .contains("├ 技能    未命中\n")
                .contains("├ 工具    3 个：webSearch, knowledge_search, promote_to_task")
                .contains("├ 上下文  记忆 2 条，知识库 1 条，任务资料 0 条，Prompt 1930 tokens（2%）")
                .contains("├ 模型    #1 model=2 首字 19.1s 总耗时 28.8s in=1930 out=210")
                .contains("├ 工具调用 无")
                .contains("├ 拆分    无（未提升为任务）")
                .contains("├ 验证    COMPLETED 完成条件已满足")
                .contains("└ 学习    短期会话 已写入；长期记忆 写入 1 条，拒绝 2 条")
                .doesNotContain("你");
    }

    @Test
    @DisplayName("Given 执行中有成功与失败的工具调用 When 生成摘要 Then 按调用顺序列出名称、执行耗时与成败")
    void should_list_tool_calls_with_duration_and_outcome() {
        // 准备参数
        var trace = directTrace();

        // 调用
        trace.observe(
                event(
                        ExecutionEventType.TOOL_CALL_STARTED,
                        0,
                        Map.of("toolCallId", "call-1", "toolName", "webSearch")));
        trace.observe(
                event(
                        ExecutionEventType.TOOL_RESULT_STARTED,
                        500,
                        Map.of("toolCallId", "call-1", "toolName", "webSearch")));
        trace.observe(
                event(
                        ExecutionEventType.TOOL_CALL_COMPLETED,
                        1_700,
                        Map.of(
                                "toolCallId",
                                "call-1",
                                "toolName",
                                "webSearch",
                                "resultState",
                                "SUCCESS")));
        trace.observe(
                event(
                        ExecutionEventType.TOOL_CALL_STARTED,
                        2_000,
                        Map.of("toolCallId", "call-2", "toolName", "queryWeather")));
        trace.observe(
                event(
                        ExecutionEventType.TOOL_CALL_FAILED,
                        2_300,
                        Map.of("toolCallId", "call-2", "toolName", "queryWeather")));
        var summary = trace.render("ON_COMPLETE", 3_000);

        // 断言
        assertThat(summary)
                .contains("├ 工具调用 #1 webSearch 1.2s 成功")
                .contains("  │           #2 queryWeather 0.3s 失败");
    }

    @Test
    @DisplayName("Given 执行进入失败收口 When 生成摘要 Then 结果为 FAILED 并列出异常类型与信息")
    void should_render_failure_when_execution_failed() {
        // 准备参数
        var trace = directTrace();

        // 调用
        trace.failure(new IllegalStateException("Assistant 定义不可执行: DRAFT"));
        trace.observe(
                event(
                        ExecutionEventType.EXECUTION_FAILED,
                        100,
                        Map.of("turnEndReason", "INTERNAL_ERROR")));
        var summary = trace.render("ON_COMPLETE", 120);

        // 断言
        assertThat(summary)
                .contains("结果=FAILED（INTERNAL_ERROR）")
                .contains("├ 角色    —")
                .contains("└ 失败    IllegalStateException Assistant 定义不可执行: DRAFT");
    }

    @Test
    @DisplayName("Given 长期记忆学习抛异常 When 尽力而为学习 Then 不外抛、摘要记录失败且执行结果保持完成")
    void should_keep_completed_result_when_long_term_learning_failed() {
        // 准备参数
        var governancePort = mock(MemoryGovernancePort.class);
        var writer = mock(MemoryWritePort.class);
        var governance = new MemoryGovernanceService(governancePort, writer);
        var trace = directTrace();
        trace.observe(event(ExecutionEventType.EXECUTION_COMPLETED, 100, Map.of()));

        // mock 方法
        when(governancePort.extract(any(), any(), any()))
                .thenThrow(new IllegalStateException("查询没有传回任何结果"));

        // 调用 + 断言
        assertThatCode(
                        () ->
                                AssistantApplicationService.learnLongTermMemory(
                                        governance, command(), "回复", true, trace))
                .doesNotThrowAnyException();
        var summary = trace.render("ON_COMPLETE", 200);
        assertThat(summary)
                .contains("结果=COMPLETED")
                .contains("长期记忆 失败：IllegalStateException 查询没有传回任何结果")
                .doesNotContain("└ 失败");
        verify(writer, never()).append(any());
    }

    @Test
    @DisplayName("Given 长期记忆未启用 When 尽力而为学习 Then 不调用学习流水线并在摘要标注未启用")
    void should_skip_learning_when_long_term_memory_disabled() {
        // 准备参数
        var governancePort = mock(MemoryGovernancePort.class);
        var governance = new MemoryGovernanceService(governancePort, mock(MemoryWritePort.class));
        var trace = directTrace();

        // 调用
        AssistantApplicationService.learnLongTermMemory(governance, command(), "回复", false, trace);

        // 断言
        assertThat(trace.render("ON_COMPLETE", 10)).contains("长期记忆 未启用");
        verify(governancePort, never()).extract(any(), any(), any());
    }

    @Test
    @DisplayName("Given 协调者节点提交了委派计划 When 生成摘要 Then 头部带 taskId 与节点键并列出子节点角色")
    void should_render_child_nodes_when_coordinator_delegated() {
        // 准备参数
        var trace =
                new ExecutionTraceRecorder(
                        true, "exec-2", "NODE", "task-1", "coordinator", "COORDINATOR");

        // 调用
        trace.plan(
                List.of(
                        new ExecutionTraceRecorder.ChildNode(
                                "researcher", "EXECUTOR", "role.research", "READY"),
                        new ExecutionTraceRecorder.ChildNode(
                                "aggregator", "AGGREGATOR", null, "PENDING")));
        var summary = trace.render("ON_COMPLETE", 1_000);

        // 断言
        assertThat(summary)
                .startsWith(
                        "[执行轨迹] executionId=exec-2 模式=NODE taskId=task-1 node=coordinator（COORDINATOR）")
                .contains("├ 拆分    2 个子节点")
                .contains("  │           researcher[EXECUTOR] 角色=role.research 状态=READY")
                .contains("  │           aggregator[AGGREGATOR] 角色=— 状态=PENDING");
    }

    @Test
    @DisplayName("Given 摘要开关关闭 When 记录并生成摘要 Then 不聚合任何信息")
    void should_not_collect_when_disabled() {
        // 准备参数
        var trace = new ExecutionTraceRecorder(false, "exec-3", "DIRECT", null, null, null);

        // 调用
        trace.profile("COORDINATOR", "role", "名称", List.of("skill"), null, List.of("a"), "1");
        trace.observe(event(ExecutionEventType.EXECUTION_COMPLETED, 0, Map.of()));

        // 断言
        assertThat(trace.enabled()).isFalse();
        assertThat(trace.render("ON_COMPLETE", 0)).contains("├ 角色    —").contains("结果=—");
    }

    private static ExecutionTraceRecorder directTrace() {
        return new ExecutionTraceRecorder(true, "exec-1", "DIRECT", null, null, null);
    }

    private ExecutionEvent event(
            ExecutionEventType type, long offsetMillis, Map<String, Object> payload) {
        return new ExecutionEvent(
                new EventId("event-" + sequence.incrementAndGet()),
                new TenantId("tenant-1"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                new TaskId("task-1"),
                new ExecutionId("exec-1"),
                new RunId("run-1"),
                null,
                sequence.get(),
                type,
                ExecutionEventStatus.RUNNING,
                ControlMode.COLLABORATIVE,
                OwnerType.ASSISTANT,
                new AssistantId("assistant-1"),
                null,
                null,
                new CorrelationId("correlation-1"),
                null,
                null,
                new ExecutionEventPayload(payload),
                T0.plusMillis(offsetMillis));
    }

    private static AssistantCommand command() {
        return new AssistantCommand(
                AssistantCommand.Operation.START,
                new TenantId("10"),
                new UserId("20"),
                new MemorySubject(new TenantId("10"), SubjectKind.USER, "20"),
                new AssistantId("system.assistant.default-user"),
                new ConversationId("conversation-1"),
                new SessionId("session-1"),
                new TaskId("task-1"),
                new ExecutionId("exec-1"),
                new RunId("run-1"),
                null,
                new CorrelationId("correlation-1"),
                null,
                new IdempotencyKey("idempotency-1"),
                ControlMode.READ_ONLY,
                null,
                null,
                0,
                "你好",
                CompletionCriteria.responseDelivered(),
                List.of(),
                TaskModelSelection.auto(),
                InvocationProfile.coordinator(
                        null,
                        AssistantInvocation.MemoryMode.DEFAULT,
                        List.of(),
                        com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent
                                .conversationalAuto(null, null)),
                T0);
    }
}
