package com.xuejiai.aaf.module.ai.agui.converter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.ExecutionEventPublicMapper;
import com.xuejiai.aaf.module.ai.agui.AafAguiEventConverter;
import com.xuejiai.aaf.module.ai.agui.AafAguiStreamContext;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 未注册类型的兜底 converter：先经公共事件脱敏，再按业务事件名二次分派。
 *
 * <p>这一层的分派键是公共事件的 {@code type} 字符串（如 {@code aaf.tool.started}）而不是 {@link ExecutionEventType}，因此不进
 * registry 的枚举分派表。工具事件收敛为 AG-UI 标准三段式，其余无一等协议类型的 安全事件降级为 {@code CUSTOM}。
 *
 * <p><b>唯一出口约束</b>：所有走到这里的事件都必须先过 {@link ExecutionEventPublicMapper}，不得直接读内部 {@code ExecutionEvent}
 * 的 payload——那会绕过脱敏。
 */
public final class PublicEventFallbackConverter implements AafAguiEventConverter {

    private final ExecutionEventPublicMapper publicMapper;

    public PublicEventFallbackConverter(ExecutionEventPublicMapper publicMapper) {
        this.publicMapper = Objects.requireNonNull(publicMapper, "publicMapper 不能为空");
    }

    /** 兜底 converter 不声明具体类型；由 registry 作为 fallback 直接持有。 */
    @Override
    public Set<ExecutionEventType> supportedTypes() {
        return Set.of();
    }

    @Override
    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        var publicEvent = publicMapper.map(event);
        var data = publicEvent.data().values();
        var toolCallId = text(data, "toolCallId");
        return switch (publicEvent.type()) {
            case "aaf.tool.started" -> {
                var toolName = text(data, "toolName");
                yield toolCallId == null || toolName == null
                        ? custom(context, publicEvent)
                        : context.toolCallStart(toolCallId, toolName);
            }
            case "aaf.tool.completed", "aaf.tool.failed" ->
                    toolCallId == null
                            ? custom(context, publicEvent)
                            : context.toolCallResult(toolCallId, JsonUtils.toJsonString(data));
            default -> custom(context, publicEvent);
        };
    }

    private static List<AguiEvent> custom(
            AafAguiStreamContext context, AafAiTaskEvent publicEvent) {
        return List.of(
                new AguiEvent.Custom(
                        context.threadId(),
                        context.runId(),
                        publicEvent.type(),
                        customValue(publicEvent)));
    }

    /**
     * CUSTOM 载荷显式建 Map，不直接传 record。
     *
     * <p>{@code AguiEventEncoder} 用 agentscope-core 的 Jackson 2 codec 序列化，其 JavaTimeModule 默认把
     * {@code Instant} 写成数字时间戳；前端 {@code isAafAiTaskEvent} 要求 {@code createdAt} 是字符串。这里固定输出
     * ISO-8601，避免依赖上游日期策略。
     */
    private static Map<String, Object> customValue(AafAiTaskEvent publicEvent) {
        var value = new LinkedHashMap<String, Object>();
        value.put("eventId", publicEvent.eventId());
        value.put("taskId", publicEvent.taskId());
        value.put("executionId", publicEvent.executionId());
        value.put("runId", publicEvent.runId());
        value.put("sessionId", publicEvent.sessionId());
        value.put("sequence", publicEvent.sequence());
        value.put("eventOffset", publicEvent.eventOffset());
        value.put("status", publicEvent.status());
        value.put("type", publicEvent.type());
        value.put("version", publicEvent.version());
        value.put("audience", publicEvent.audience().name());
        value.put("delivery", publicEvent.delivery().name());
        value.put("data", publicEvent.data().values());
        value.put("createdAt", isoInstant(publicEvent.createdAt()));
        return value;
    }

    private static String isoInstant(Instant instant) {
        return instant == null ? Instant.EPOCH.toString() : instant.toString();
    }

    private static String text(Map<String, Object> values, String key) {
        var value = values.get(key);
        return value instanceof String text ? text : null;
    }
}
