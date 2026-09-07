package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;

import reactor.core.publisher.Mono;

/**
 * 结构化信息卡片内置工具（AAF-114 #11408 第二版收窄）。
 *
 * <p>只保留 {@code INFO_CARD}——纯只读展示，无需人类响应，适合工具 + tool-call 消息 part 模式。原 {@code CHOICE}/{@code FORM}
 * 分支已移除：需要人类响应的 Clarification 场景改为复用标准 AG-UI interrupt/resume 协议（{@code RunLifecycleEventConverter}
 * 把 {@code CLARIFICATION_REQUESTED} 投影为 {@code reason="input_required"} 的 interrupt，前端用官方 {@code
 * unstable_getPendingInterrupts}/ {@code unstable_submitInterruptResponses}
 * 处理），不再依赖本工具承载可提交表单——避免维护两套并行的 "需要人类介入"机制。
 *
 * <p>模型显式调用本工具声明要展示的信息卡片，工具执行时严格校验结构（不信任模型输出），校验通过后原样返回校验后的 JSON。前端复用 assistant-ui 官方 tool-call 消息
 * part 机制渲染。
 */
public final class RenderUiBlockTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "render_ui_block";

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "在对话中展示只读信息摘要卡片（标题 + 可选说明 + 可选条目列表）。" + "仅用于纯展示场景；需要用户填写或选择的场景由服务端澄清机制自动处理，不通过本工具。";
    }

    /** 纯展示型工具，不产生任何业务副作用。 */
    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "title",
                        Map.of("type", "string", "description", "卡片标题"),
                        "description",
                        Map.of("type", "string", "description", "补充说明，可选"),
                        "items",
                        Map.of(
                                "type", "array",
                                "description", "展示条目列表，元素为 {label, value}",
                                "items",
                                        Map.of(
                                                "type", "object",
                                                "properties",
                                                        Map.of(
                                                                "label", Map.of("type", "string"),
                                                                "value", Map.of("type", "string")),
                                                "required", List.of("label", "value")))),
                "required",
                List.of("title"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> render(invocation.arguments()));
    }

    private ToolInvocationResult render(Map<String, Object> arguments) {
        var title = requireText(arguments, "title");
        var id = "ui-block-" + UUID.randomUUID();
        var block = new LinkedHashMap<String, Object>();
        block.put("version", 1);
        block.put("id", id);
        block.put("type", "INFO_CARD");
        block.put("title", title);
        var description = arguments.get("description");
        if (description instanceof String text && !text.isBlank()) {
            block.put("description", text);
        }
        var items = arguments.get("items");
        if (items instanceof List<?> list) {
            block.put("items", list.stream().map(this::requireInfoItem).toList());
        }
        var json = JsonUtils.toJsonString(block);
        return new ToolInvocationResult(json, Map.of("type", "INFO_CARD", "blockId", id));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireInfoItem(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("items 元素必须是对象");
        }
        var item = (Map<String, Object>) map;
        return Map.of(
                "label", requireText(item, "label"),
                "value", requireText(item, "value"));
    }

    private static String requireText(Map<String, Object> arguments, String field) {
        var value = arguments.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("render_ui_block 需要非空 " + field);
        }
        return value.toString().trim();
    }
}
