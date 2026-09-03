package com.xuejiai.aaf.module.ai.agui.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.ExecutionEventPublicMapper;
import com.xuejiai.aaf.module.ai.agui.AafAguiEventConverter;
import com.xuejiai.aaf.module.ai.agui.AafAguiStreamContext;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 内部节点（非交付者）事件的投影：降级为 {@code CUSTOM}，payload 带 source 路径与原始事件类型；子任务生命周期事件 额外追加标准 {@code
 * StepStarted}/{@code StepFinished}（阶段通知）、{@code StateSnapshot}/{@code StateDelta}（全局状态看板）与 {@code
 * ActivitySnapshot}/{@code ActivityDelta}（对话时间线活动卡片，AAF-104 #10403）——四者都不替代 CUSTOM，是附加产出。
 *
 * <p><b>为什么标准事件是 CUSTOM 的兜底，不是替代</b>：AG-UI 的 {@code TEXT_MESSAGE_*} 语义是"面向用户的最终助手回复"。
 * 执行者、评估者的输出是中间产物，混入标准事件会让客户端把碎片当主回复渲染。官方 {@code SubagentEventConverter} 正是这样 处理—— {@code source !=
 * null} 的事件一律 CUSTOM，且不按类型注册、只在有 source 时被调用。本类是同一策略在 AAF 平级编排下的等价实现：AAF 的 source 由 {@link
 * NodeIdentity#sourcePath()} 自行合成，而非依赖 core（core 在平级编排下 恒为 null）。{@code StepStarted}/{@code
 * StepFinished}/{@code StateSnapshot}/{@code StateDelta}/{@code ActivitySnapshot}/{@code
 * ActivityDelta} 是例外——它们不是"最终正文"，可以在 CUSTOM 之外额外发出标准事件供客户端 统一处理，不需要客户端解析 CUSTOM payload。
 *
 * <p>与官方一致地按事件族给 CUSTOM 起名，客户端可只订阅关心的族；完整原始类型放在 payload 的 {@code type} 里。
 *
 * <p><b>脱敏</b>：数据一律取自 {@link ExecutionEventPublicMapper} 的公共事件，不直接读内部 payload。
 */
public final class InternalNodeEventConverter implements AafAguiEventConverter {

    private static final String NAME_LIFECYCLE = "aaf.node.lifecycle";
    private static final String NAME_MESSAGE = "aaf.node.message";
    private static final String NAME_TOOL = "aaf.node.tool";
    private static final String NAME_OTHER = "aaf.node.event";
    private static final String STEP_EXECUTION = "execution";

    private final ExecutionEventPublicMapper publicMapper;

    public InternalNodeEventConverter(ExecutionEventPublicMapper publicMapper) {
        this.publicMapper = Objects.requireNonNull(publicMapper, "publicMapper 不能为空");
    }

    /** 不按类型注册：由 registry 在识别出「事件来自内部节点」时直接调用，与官方 SubagentEventConverter 同构。 */
    @Override
    public Set<ExecutionEventType> supportedTypes() {
        return Set.of();
    }

    @Override
    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        var node = event.nodeIdentity();
        var publicEvent = publicMapper.map(event);
        var value =
                Map.<String, Object>of(
                        "source", node.sourcePath(),
                        "nodeKind", node.kind().name(),
                        "roleKey", node.roleKey() == null ? "" : node.roleKey(),
                        "type", publicEvent.type(),
                        "status", publicEvent.status(),
                        "data", publicEvent.data().values());
        var custom =
                new AguiEvent.Custom(
                        context.threadId(), context.runId(), name(event.type()), value);
        var events = new ArrayList<AguiEvent>();
        events.addAll(stateEvent(event, node, context));
        events.addAll(activityEvent(event, node, context));
        events.addAll(stepEvent(event.type(), context));
        events.add(custom);
        return List.copyOf(events);
    }

    /**
     * 子任务活动卡片投影（AAF-104 #10403），与 CUSTOM/Step/State 投影一并追加，不替代它们。
     *
     * <p>与 {@link #stateEvent} 同源、同触发时机，但服务不同 UI 位置——State 是全局共享文档，Activity 是嵌入 对话消息时间线的独立卡片，见
     * {@code AafAguiStreamContext#subTaskActivityChanged} 的完整设计说明 （含已知的 assistant-ui 前端渲染缺口记录）。
     */
    private static List<AguiEvent> activityEvent(
            ExecutionEvent event, NodeIdentity node, AafAguiStreamContext context) {
        return switch (event.type()) {
            case EXECUTION_STARTED, EXECUTION_COMPLETED, EXECUTION_FAILED, EXECUTION_CANCELED ->
                    context.subTaskActivityChanged(
                            node.subTaskId(),
                            node.kind().name(),
                            node.roleKey(),
                            event.status().name());
            default -> List.of();
        };
    }

    /**
     * 子任务状态看板投影（AAF-104 #10403），与 CUSTOM/Step 投影一并追加，不替代它们。
     *
     * <p>只在子任务生命周期终态/起始事件更新，与 {@link #stepEvent} 同源但语义不同——Step 是"事件流形式的阶段通知"， State
     * 是"结构化状态快照"，两者互补，分别服务 assistant-ui 的 {@code useAgUiState}（状态看板 UI）与 事件订阅（进度提示）两类不同消费场景。
     */
    private static List<AguiEvent> stateEvent(
            ExecutionEvent event, NodeIdentity node, AafAguiStreamContext context) {
        return switch (event.type()) {
            case EXECUTION_STARTED, EXECUTION_COMPLETED, EXECUTION_FAILED, EXECUTION_CANCELED ->
                    context.subTaskStateChanged(
                            node.subTaskId(),
                            node.kind().name(),
                            node.roleKey(),
                            event.status().name());
            default -> List.of();
        };
    }

    /**
     * 子任务节点的 execution 阶段边界（AAF-104 #10403），与 CUSTOM 投影一并追加，不替代它。
     *
     * <p><b>为什么不让 {@code EXECUTION_*} 绕过内部节点降级独立分派</b>：{@code RunLifecycleEventConverter}
     * 对同一组类型的处理是"per-run 状态操作"（{@code context.runStarted()}/{@code runFinishedOnce()}）， 若子任务的
     * {@code EXECUTION_STARTED} 也绕过降级直达该 converter，会把子任务的开始/结束误当整个 run 的开始/结束。 因此阶段边界作为本 converter
     * 的附加产出，而不是独立抢占类型分派权。
     *
     * <p><b>不区分 aggregation 阶段（已核实确认）</b>：{@code CoordinationPlan.AggregationContract.Kind
     * .AGGREGATOR_REDUCE} 下确有专门的 {@code AGGREGATOR} 智能体执行归约，但按 {@code
     * AssistantCommand.nodeIdentityOf} 的交付角色算定，此时 {@code AGGREGATOR} 恰好是唯一交付者 （{@code
     * delivery=true}），走 {@code RunLifecycleEventConverter} 的根节点路径而非本类——客户端视角是 "run
     * 本身在收尾"（`RunStarted` 早已发出，聚合完成即 `RunFinished`），不需要独立 Step 包装。 {@code PASS_THROUGH}/{@code
     * ORDERED_CONCAT} 两种契约则是纯拼接，没有独立智能体执行聚合工作。 走到本类的子任务节点（{@code EXECUTOR}/{@code EVALUATOR}
     * 恒内部，{@code COORDINATOR} 仅 {@code AGGREGATOR_REDUCE} 下为内部）统一映射为 {@code "execution"}。
     */
    private static List<AguiEvent> stepEvent(
            ExecutionEventType type, AafAguiStreamContext context) {
        return switch (type) {
            case EXECUTION_STARTED -> context.stepStarted(STEP_EXECUTION);
            case EXECUTION_COMPLETED, EXECUTION_FAILED, EXECUTION_CANCELED ->
                    context.stepFinished(STEP_EXECUTION);
            default -> List.of();
        };
    }

    /** 按事件族分名，便于客户端按需订阅；细分类型仍在 payload 的 type 字段里。 */
    private static String name(ExecutionEventType type) {
        return switch (type) {
            case RUN_STARTED,
                    RUN_COMPLETED,
                    RUN_FAILED,
                    EXECUTION_STARTED,
                    EXECUTION_COMPLETED,
                    EXECUTION_FAILED,
                    EXECUTION_CANCELED ->
                    NAME_LIFECYCLE;
            case MESSAGE_STARTED, MESSAGE_DELTA, MESSAGE_COMPLETED -> NAME_MESSAGE;
            case TOOL_CALL_STARTED, TOOL_CALL_COMPLETED, TOOL_CALL_FAILED -> NAME_TOOL;
            default -> NAME_OTHER;
        };
    }
}
