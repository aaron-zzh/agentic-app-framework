package com.xuejiai.aaf.module.ai.agui.converter;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.module.ai.agui.AafAguiEventConverter;
import com.xuejiai.aaf.module.ai.agui.AafAguiStreamContext;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * planning / verification 阶段成对投影（AAF-104 #10403）。
 *
 * <p><b>execution / aggregation 阶段不在本类</b>：子任务的开始/完成事实是通用的 {@code EXECUTION_STARTED}/{@code
 * EXECUTION_COMPLETED}（{@code TaskCommandService.executeNode} 调 {@code commands.execute} 产生， 带
 * {@code nodeIdentity}），这批事件在非根节点上会走"内部节点降级 CUSTOM"规则，若本类抢占其类型分派会与 {@code
 * RunLifecycleEventConverter} 的 per-run 语义冲突（子任务开始会被误当整个 run 开始）。因此 execution/aggregation 阶段边界改为
 * {@code InternalNodeEventConverter} 在 CUSTOM 投影之外的附加产出，见该类 Javadoc。
 *
 * <p><b>{@code SUBTASK_STARTED}/{@code SUBTASK_COMPLETED}/{@code SUBTASK_FAILED}/{@code
 * SUBTASK_CANCELED} 已删除（AAF-104 核实确认）</b>：这四个类型是 {@code TaskCommandService} 落地前的预留占位，全仓核实从未被任何
 * 生产代码发出——实际实现选择复用更早已存在的 {@code EXECUTION_STARTED}/{@code EXECUTION_COMPLETED}（配合 {@code
 * nodeIdentity} 区分节点），预留值因此变成孤儿枚举，已随本次改动一并清理，不留死代码。
 *
 * <p><b>不受"内部节点降级 CUSTOM"规则约束</b>：{@code EXECUTOR_PLAN_*}/{@code VALIDATION_*} 目前均不携带 {@code
 * nodeIdentity}（{@code VALIDATION_*} 的构造调用点尚未接入 {@code nodeIdentity}，是 #10407
 * 遗留的已知缺口，记入改进意见池，非本次任务范围），因此不会触发内部节点降级判断，声明本方法只是为了与 {@code AafAguiConverterRegistry}
 * 的分派协议保持显式一致，不依赖其实际生效。
 */
public final class StepEventConverter implements AafAguiEventConverter {

    private static final String PLANNING = "planning";
    private static final String VERIFICATION = "verification";

    @Override
    public Set<ExecutionEventType> supportedTypes() {
        return Set.of(
                ExecutionEventType.EXECUTOR_PLAN_CREATED,
                ExecutionEventType.EXECUTOR_PLAN_SUBMITTED,
                ExecutionEventType.VALIDATION_STARTED,
                ExecutionEventType.VALIDATION_COMPLETED,
                ExecutionEventType.VALIDATION_FAILED);
    }

    @Override
    public boolean bypassesInternalNodeDowngrade() {
        return true;
    }

    @Override
    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        return switch (event.type()) {
            case EXECUTOR_PLAN_CREATED, EXECUTOR_PLAN_SUBMITTED -> context.stepStarted(PLANNING);
            case VALIDATION_STARTED -> context.stepStarted(VERIFICATION);
            case VALIDATION_COMPLETED, VALIDATION_FAILED -> context.stepFinished(VERIFICATION);
            default ->
                    throw new IllegalStateException(
                            "StepEventConverter 收到未声明支持的类型: " + event.type());
        };
    }
}
