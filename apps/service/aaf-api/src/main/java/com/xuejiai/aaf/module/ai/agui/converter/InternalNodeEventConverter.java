package com.xuejiai.aaf.module.ai.agui.converter;

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
 * 内部节点（非交付者）事件的投影：一律降级为 {@code CUSTOM}，payload 带 source 路径与原始事件类型。
 *
 * <p><b>为什么不发标准事件</b>：AG-UI 的 {@code TEXT_MESSAGE_*} 语义是"面向用户的最终助手回复"。执行者、评估者的输出是
 * 中间产物，混入标准事件会让客户端把碎片当主回复渲染。官方 {@code SubagentEventConverter} 正是这样处理—— {@code source != null} 的事件一律
 * CUSTOM，且不按类型注册、只在有 source 时被调用。本类是同一策略在 AAF 平级编排下的 等价实现：AAF 的 source 由 {@link
 * NodeIdentity#sourcePath()} 自行合成，而非依赖 core（core 在平级编排下恒为 null）。
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
        return List.of(
                new AguiEvent.Custom(
                        context.threadId(), context.runId(), name(event.type()), value));
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
