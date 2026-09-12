package com.xuejiai.aaf.module.ai.agui.converter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.module.ai.agui.AafAguiEventConverter;
import com.xuejiai.aaf.module.ai.agui.AafAguiStreamContext;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * run 生命周期与终态投影。
 *
 * <p>{@code RUN_FAILED} 是叶子 Agent 失败，{@code EXECUTION_*} 是任务级终态；两者都必须收敛为 AG-UI 的 {@code
 * RUN_ERROR}，否则前端只能收到 CUSTOM，无法识别失败。错误码只暴露稳定枚举字符串，不带异常原文。
 *
 * <p><b>{@code AUTHORIZATION_REQUESTED} 收敛为 AG-UI 标准 interrupt 终态</b>（AAF-104 #10404，已核实修正）： 与官方
 * Interrupts 契约对齐——run 暂停等待人工审批不是失败，而是携带 {@code outcome:{type:"interrupt"}} 的 {@code RunFinished}。
 *
 * <p><b>不是 {@code APPROVAL_REQUESTED}</b>（曾误判，已核实纠正）：AgentScope core 自带的权限系统（{@code
 * RequireUserConfirmEvent}/{@code ConfirmResult}，映射为 {@code APPROVAL_REQUESTED}/{@code
 * APPROVAL_RESOLVED}）需要在构建 {@code ReActAgent} 时配置 {@code PermissionContextState} 才会触发——全仓核实 AAF
 * 从未配置，是死代码路径。真正驱动 AAF 授权确认的是完全独立的应用层治理——{@code DefaultToolGateway.invoke} → {@code
 * ToolAuthorizationContext.requireVisible} → {@code HumanApproval} 持久化（{@code
 * AUTHORIZATION_REQUESTED}/{@code AUTHORIZATION_GRANTED}/{@code AUTHORIZATION_DENIED}），这是每次真实工具
 * 调用都会走的代码路径。
 *
 * <p>只有根节点（DIRECT 直答或协调者自身）的授权等待才会走到本 converter——子任务的授权等待由 {@code TaskCommandService}
 * 处理为"该子任务本轮让出并发位、稍后重试"，不终止整个 run，其事件因 {@code nodeIdentity != null} 会先被 {@code
 * InternalNodeEventConverter} 降级为 CUSTOM，不会到达本类——分派顺序本身 保证了这个边界，不需要额外判断排除子任务。
 *
 * <p><b>{@code CLARIFICATION_REQUESTED} 同样收敛为标准 interrupt</b>（AAF-114 #11408 第二版）： {@code
 * reason="input_required"} 对齐官方标准值语义；{@code responseSchema} 由 {@link
 * ClarificationResponseSchema#fromQuestions} 从 payload 的 {@code questions} 派生。与 {@code
 * AUTHORIZATION_REQUESTED} 共用同一套 interrupt/resume 传输层，但底层领域模型（{@code HumanApproval} vs {@code
 * ClarificationRequest}）保持独立——只在 {@code AssistantAguiController#resumeRun} 按 {@code interruptId}
 * 归属分派。
 */
public final class RunLifecycleEventConverter implements AafAguiEventConverter {

    @Override
    public Set<ExecutionEventType> supportedTypes() {
        return Set.of(
                ExecutionEventType.EXECUTION_STARTED,
                ExecutionEventType.EXECUTION_PROMOTED,
                ExecutionEventType.EXECUTION_COMPLETED,
                ExecutionEventType.EXECUTION_FAILED,
                ExecutionEventType.EXECUTION_CANCELED,
                ExecutionEventType.COMMAND_REJECTED,
                ExecutionEventType.RUN_FAILED,
                ExecutionEventType.AUTHORIZATION_REQUESTED,
                ExecutionEventType.CLARIFICATION_REQUESTED);
    }

    @Override
    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        return switch (event.type()) {
            case EXECUTION_STARTED -> context.runStarted();
            case EXECUTION_PROMOTED, EXECUTION_COMPLETED -> context.runFinishedOnce();
            case EXECUTION_FAILED, EXECUTION_CANCELED, COMMAND_REJECTED, RUN_FAILED ->
                    context.runError(errorCode(event));
            case AUTHORIZATION_REQUESTED -> context.runInterrupted(List.of(interrupt(event)));
            case CLARIFICATION_REQUESTED ->
                    context.runInterrupted(List.of(clarificationInterrupt(event)));
            default ->
                    throw new IllegalStateException(
                            "RunLifecycleEventConverter 收到未声明支持的类型: " + event.type());
        };
    }

    /**
     * {@code approvalId} 直接作为 {@code interruptId}——{@code DefaultToolGateway.invoke} 每次授权等待 唯一对应一条
     * {@code HumanApproval} 记录，不需要像 {@code replyId:toolCallId} 那样额外派生复合键。 {@code
     * reason="tool_call"} 对齐官方核心值语义（{@code action} 即工具名，本 interrupt 绑定具体工具调用）。
     */
    private static AguiEvent.Interrupt interrupt(ExecutionEvent event) {
        var values = event.payload().values();
        var approvalId = (String) values.get("approvalId");
        var action = (String) values.get("action");
        var reason = (String) values.get("reason");
        var reversible = (Boolean) values.get("reversible");
        return new AguiEvent.Interrupt(
                approvalId,
                "tool_call",
                reason + "：" + action,
                action,
                null,
                null,
                Map.of("reversible", reversible));
    }

    /**
     * {@code requestId} 直接作为 {@code interruptId}——与 {@code approvalId} 同一模式，一次澄清请求对应一个
     * interrupt（不是每个字段一个），多字段体现在 {@code responseSchema.properties} 里。
     */
    private static AguiEvent.Interrupt clarificationInterrupt(ExecutionEvent event) {
        var values = event.payload().values();
        var requestId = (String) values.get("requestId");
        @SuppressWarnings("unchecked")
        var questions = (List<Object>) values.get("questions");
        @SuppressWarnings("unchecked")
        var requiredFields = (List<String>) values.get("requiredFields");
        var responseSchema = ClarificationResponseSchema.fromQuestions(questions, requiredFields);
        if (event.taskId() == null) {
            throw new IllegalStateException("结构化澄清事件必须先 promotion 为 Task");
        }
        return new AguiEvent.Interrupt(
                requestId,
                "input_required",
                "需要补充参数",
                null,
                responseSchema,
                null,
                Map.of("taskId", event.taskId().value()));
    }

    private static String errorCode(ExecutionEvent event) {
        return switch (event.type()) {
            case EXECUTION_CANCELED -> "RUN_CANCELED";
            case COMMAND_REJECTED -> "ASSISTANT_COMMAND_REJECTED";
            case RUN_FAILED -> "AGENT_RUN_FAILED";
            default -> "ASSISTANT_EXECUTION_FAILED";
        };
    }
}
