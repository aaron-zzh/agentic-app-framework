package com.xuejiai.aaf.module.ai.agui;

import com.xuejiai.aaf.common.util.JsonUtils;

import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.agentscope.core.agui.event.AguiEvent;
import tools.jackson.databind.node.ObjectNode;

/**
 * AG-UI 事件序列化：委托官方 {@link AguiEventEncoder}，并修正其 {@code Interrupt} 缺少 NON_NULL 的问题。
 *
 * <p>agentscope-extensions-agui 2.0.4 的 {@code RunFinished} 标注了 {@code @JsonInclude(NON_NULL)}，但嵌套的
 * {@code AguiEvent.Interrupt} 没有，未设置的 {@code toolCallId}/{@code responseSchema}/{@code expiresAt}
 * 会输出为 {@code null}。前端 {@code @ag-ui/core} 的 zod 校验只接受缺省（undefined）而非 null，导致 interrupt
 * 终态被整体拒收、审批卡与澄清表单无法弹出。 这里只在 interrupt 终态上去掉 null 字段，其余事件原样输出。
 */
public final class AafAguiEncoder {

    private static final AguiEventEncoder DELEGATE = new AguiEventEncoder();

    private AafAguiEncoder() {}

    /** 与 {@link AguiEventEncoder#encodeToJson} 相同的输出格式（带前导空格，供 SSE {@code data:} 拼接）。 */
    public static String encodeToJson(AguiEvent event) {
        var json = DELEGATE.encodeToJson(event);
        if (!(event instanceof AguiEvent.RunFinished)) {
            return json;
        }
        var root = JsonUtils.readTree(json.strip());
        var interrupts = root.path("outcome").path("interrupts");
        if (!interrupts.isArray() || interrupts.isEmpty()) {
            return json;
        }
        for (var interrupt : interrupts) {
            if (interrupt instanceof ObjectNode object) {
                var nullFields = new java.util.ArrayList<String>();
                for (var name : object.propertyNames()) {
                    if (object.get(name).isNull()) {
                        nullFields.add(name);
                    }
                }
                nullFields.forEach(object::remove);
            }
        }
        return " " + JsonUtils.toJsonString(root);
    }
}
