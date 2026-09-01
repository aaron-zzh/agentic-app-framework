package com.xuejiai.aaf.module.ai.agui;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 事件类型到 converter 的分派表。
 *
 * <p><b>拒绝重复注册</b>：同一 {@link ExecutionEventType} 被两个 converter 声明时构造直接失败，不采用"后注册静默覆盖"。 AAF
 * 的公共事件是安全合同而非插件优先级——静默覆盖意味着某个事件的脱敏规则可能被无声替换，而这类问题在生产上表现为 信息泄漏或前端信息缺失，都很难回溯到注册顺序。
 *
 * <p>未注册类型交给 {@code fallback}：它按公共事件的 {@code type} 字符串再分派（工具三段式 / CUSTOM 兜底）， 因为那一层的键不是 {@code
 * ExecutionEventType} 而是业务事件名。
 */
public final class AafAguiConverterRegistry {

    private final Map<ExecutionEventType, AafAguiEventConverter> byType =
            new EnumMap<>(ExecutionEventType.class);
    private final AafAguiEventConverter fallback;

    public AafAguiConverterRegistry(
            List<AafAguiEventConverter> converters, AafAguiEventConverter fallback) {
        Objects.requireNonNull(converters, "converters 不能为空");
        this.fallback = Objects.requireNonNull(fallback, "fallback 不能为空");
        for (var converter : converters) {
            for (var type : converter.supportedTypes()) {
                var existing = byType.putIfAbsent(type, converter);
                if (existing != null) {
                    throw new IllegalStateException(
                            "AG-UI converter 重复注册: type="
                                    + type
                                    + "，已注册="
                                    + existing.getClass().getSimpleName()
                                    + "，冲突="
                                    + converter.getClass().getSimpleName());
                }
            }
        }
    }

    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        return byType.getOrDefault(event.type(), fallback).convert(event, context);
    }
}
