package com.xuejiai.aaf.module.ai.agui;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 单一事件族的 AG-UI 投影规则。
 *
 * <p>拆成小类而不是一个大 switch，是为了让每族的配对与脱敏规则可以独立单测。所有配对类事件（message / tool call / run 终态） 必须通过 {@link
 * AafAguiStreamContext} 发射，实现类不得自行构造——否则每加一个 converter 都可能绕过配对不变量。
 *
 * <p><b>不复用官方 {@code AgentEventConverterRegistry}</b>：官方 converter 的输入固定为 AgentScope {@code
 * AgentEvent}， 直接套用会绕过 AAF 的租户、TaskBoard 与证据脱敏边界。这里采用同构结构但输入是 AAF 自己的执行事件。
 */
public interface AafAguiEventConverter {

    /** 本 converter 负责的事件类型；registry 按此建立分派表并拒绝重复注册。 */
    Set<ExecutionEventType> supportedTypes();

    /** 产出 0..N 个 AG-UI 事件；返回空表示该事件在当前状态下无需对外投影（如重复 START）。 */
    List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context);

    /**
     * 是否绕过"内部节点一律降级 CUSTOM"的分派规则，无论事件来自根节点还是非根节点都按类型分派本 converter。
     *
     * <p>默认 {@code false}（维持既有行为：内部节点事件优先降级 CUSTOM）。只有进度类信息（如阶段边界）需要对所有节点 一致投影时才应覆写为 {@code
     * true}——"面向用户的最终正文"类事件（文本消息、工具结果）绝不应绕过该规则，否则执行者的 中间产物会被客户端当最终回复渲染。
     */
    default boolean bypassesInternalNodeDowngrade() {
        return false;
    }
}
