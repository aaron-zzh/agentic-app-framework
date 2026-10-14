package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.event.NodeIdentity;

/**
 * 一次任务实时流的交付状态跟踪（ADR-008）。
 *
 * <p>节点的 {@code delivery} 标志是节点启动时刻算定的：计划里没有聚合者时协调者是交付者。协调者随后成功提交 {@code delegate_agents}
 * 后，交付权移交给聚合者，协调者不再是交付者。这个动态变化只能从事件流中观察到，因此 AG-UI 投影与实时流收口各自持有一个实例， 按同一事件顺序串行喂入，得到一致的判定。
 *
 * <p>非线程安全地假设单个事件流串行喂入；内部集合使用并发集合只为兼容跨线程的可见性。
 */
public final class RunDeliveryTracker {

    private final Set<String> delegatedNodes = ConcurrentHashMap.newKeySet();

    /** 观察事件：协调者的 {@code delegate_agents} 调用成功，即该协调者已移交交付权。 */
    public void observe(ExecutionEvent event) {
        Objects.requireNonNull(event, "event 不能为空");
        var node = event.nodeIdentity();
        if (node != null
                && node.kind() == NodeIdentity.NodeKind.COORDINATOR
                && event.type() == ExecutionEventType.TOOL_CALL_COMPLETED
                && SubmitCoordinationPlanTool.TOOL_NAME.equals(
                        event.payload().values().get("toolName"))) {
            delegatedNodes.add(node.nodeId());
        }
    }

    /** 节点当前是否仍是交付者：启动时被算定为交付者，且尚未移交。 */
    public boolean delivers(NodeIdentity node) {
        Objects.requireNonNull(node, "node 不能为空");
        return node.userFacing() && !delegatedNodes.contains(node.nodeId());
    }

    /**
     * 事件是否让整个任务实时流收口：交付节点完成，或任一节点失败、取消、暂停、进入等待授权 / 澄清。
     *
     * <p>多节点任务不会在协调者收口时截断聚合者输出：协调者委派成功后不再是交付者，其完成事件不结束流。
     */
    public boolean endsStream(ExecutionEvent event) {
        observe(event);
        var node = event.nodeIdentity();
        if (event.type() == ExecutionEventType.EXECUTION_COMPLETED) {
            return node == null || delivers(node);
        }
        return switch (event.type()) {
            case EXECUTION_FAILED,
                    EXECUTION_CANCELED,
                    EXECUTION_PAUSED,
                    COMMAND_REJECTED,
                    AUTHORIZATION_REQUESTED,
                    CLARIFICATION_REQUESTED ->
                    true;
            default -> false;
        };
    }
}
