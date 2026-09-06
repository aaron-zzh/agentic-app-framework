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
 * 结构化展示卡片内置工具（AAF-114 官方模式改造，替代原 CUSTOM 事件路径）。
 *
 * <p>模型显式调用本工具声明要展示的卡片，工具执行时严格校验结构（不信任模型输出），校验通过后原样返回校验后的
 * JSON。前端复用 assistant-ui 官方 tool-call 消息 part 机制渲染，不再依赖 AG-UI CUSTOM 事件旁路。
 *
 * <p>纯展示型工具，无业务副作用——见 {@link #readOnly()}。三种类型对齐前端
 * {@code AafUiBlock} 协议（apps/webui/.../ui-block/aaf-ui-block.ts），字段定义必须与之保持一致，
 * 修改任一侧字段前先检查另一侧是否同步。
 */
public final class RenderUiBlockTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "render_ui_block";

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "在对话中展示结构化卡片。type=INFO_CARD 用于展示只读信息摘要；"
                + "type=CHOICE 用于展示待用户确认的选项列表；type=FORM 用于展示待用户填写的字段表单。"
                + "CHOICE/FORM 必须提供 clarificationId 关联既有澄清请求，不得凭空构造。";
    }

    /** 纯展示型工具，不产生任何业务副作用——真正的状态变更由 Clarification 事务和事件产生，见类注释。 */
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
                        "type",
                        Map.of(
                                "type", "string",
                                "enum", List.of("INFO_CARD", "CHOICE", "FORM"),
                                "description", "卡片类型"),
                        "title",
                        Map.of("type", "string", "description", "卡片标题"),
                        "description",
                        Map.of("type", "string", "description", "INFO_CARD 的补充说明，可选"),
                        "items",
                        Map.of(
                                "type", "array",
                                "description", "INFO_CARD 的展示条目列表，元素为 {label, value}",
                                "items",
                                        Map.of(
                                                "type", "object",
                                                "properties",
                                                        Map.of(
                                                                "label", Map.of("type", "string"),
                                                                "value", Map.of("type", "string")),
                                                "required", List.of("label", "value"))),
                        "clarificationId",
                        Map.of("type", "string", "description", "CHOICE/FORM 关联的既有澄清请求 ID"),
                        "options",
                        Map.of(
                                "type", "array",
                                "description", "CHOICE 的可选项列表，元素为 {id, label, description?}",
                                "items",
                                        Map.of(
                                                "type", "object",
                                                "properties",
                                                        Map.of(
                                                                "id", Map.of("type", "string"),
                                                                "label", Map.of("type", "string"),
                                                                "description",
                                                                        Map.of(
                                                                                "type",
                                                                                "string")),
                                                "required", List.of("id", "label"))),
                        "multiple",
                        Map.of("type", "boolean", "description", "CHOICE 是否允许多选，默认 false"),
                        "fields",
                        Map.of(
                                "type", "array",
                                "description",
                                        "FORM 的字段列表，元素为 {key, label, type, required?, options?, constraints?}",
                                "items", Map.of("type", "object"))),
                "required",
                List.of("type", "title"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> render(invocation.arguments()));
    }

    private ToolInvocationResult render(Map<String, Object> arguments) {
        var type = requireText(arguments, "type");
        var title = requireText(arguments, "title");
        var id = "ui-block-" + UUID.randomUUID();
        var block = new LinkedHashMap<String, Object>();
        block.put("version", 1);
        block.put("id", id);
        block.put("type", type);
        block.put("title", title);
        switch (type) {
            case "INFO_CARD" -> renderInfoCard(arguments, block);
            case "CHOICE" -> renderChoice(arguments, block);
            case "FORM" -> renderForm(arguments, block);
            default ->
                    throw new IllegalArgumentException(
                            "render_ui_block type 仅支持 INFO_CARD、CHOICE、FORM: " + type);
        }
        var json = JsonUtils.toJsonString(block);
        return new ToolInvocationResult(json, Map.of("type", type, "blockId", id));
    }

    private void renderInfoCard(Map<String, Object> arguments, Map<String, Object> block) {
        var description = arguments.get("description");
        if (description instanceof String text && !text.isBlank()) {
            block.put("description", text);
        }
        var items = arguments.get("items");
        if (items instanceof List<?> list) {
            var validated = list.stream().map(this::requireInfoItem).toList();
            block.put("items", validated);
        }
    }

    private void renderChoice(Map<String, Object> arguments, Map<String, Object> block) {
        var clarificationId = requireText(arguments, "clarificationId");
        block.put("clarificationId", clarificationId);
        var options = arguments.get("options");
        if (!(options instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("render_ui_block type=CHOICE 需要非空 options");
        }
        block.put("options", list.stream().map(this::requireChoiceOption).toList());
        block.put("multiple", Boolean.TRUE.equals(arguments.get("multiple")));
    }

    private void renderForm(Map<String, Object> arguments, Map<String, Object> block) {
        var clarificationId = requireText(arguments, "clarificationId");
        block.put("clarificationId", clarificationId);
        var fields = arguments.get("fields");
        if (!(fields instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("render_ui_block type=FORM 需要非空 fields");
        }
        block.put("fields", list.stream().map(this::requireFormField).toList());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireChoiceOption(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("options 元素必须是对象");
        }
        var option = (Map<String, Object>) map;
        var result = new LinkedHashMap<String, Object>();
        result.put("id", requireText(option, "id"));
        result.put("label", requireText(option, "label"));
        var description = option.get("description");
        if (description instanceof String text && !text.isBlank()) {
            result.put("description", text);
        }
        return result;
    }

    private static final List<String> ALLOWED_FIELD_TYPES =
            List.of("text", "textarea", "number", "select", "checkbox");

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireFormField(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("fields 元素必须是对象");
        }
        var field = (Map<String, Object>) map;
        var fieldType = requireText(field, "type");
        if (!ALLOWED_FIELD_TYPES.contains(fieldType)) {
            throw new IllegalArgumentException("fields.type 不支持: " + fieldType);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("key", requireText(field, "key"));
        result.put("label", requireText(field, "label"));
        result.put("type", fieldType);
        if (field.get("required") instanceof Boolean required) {
            result.put("required", required);
        }
        if (field.get("options") instanceof List<?> options) {
            result.put("options", options.stream().map(this::requireChoiceOption).toList());
        }
        if (field.get("constraints") instanceof Map<?, ?> constraints) {
            result.put("constraints", constraints);
        }
        return result;
    }

    private static String requireText(Map<String, Object> arguments, String field) {
        var value = arguments.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("render_ui_block 需要非空 " + field);
        }
        return value.toString().trim();
    }
}
