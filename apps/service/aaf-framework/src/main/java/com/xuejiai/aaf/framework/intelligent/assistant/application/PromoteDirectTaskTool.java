package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationRolePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DirectPromotionPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 当前 Assistant 将尚无副作用的 DIRECT execution 提升为 canonical Task 的唯一模型入口。 */
@Slf4j
public final class PromoteDirectTaskTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = DirectPromotionPort.PROMOTE_TOOL_NAME;

    private static final Set<String> SHAPES = Set.of("STEPS", "DELEGATE");

    private final TaskCommandService tasks;
    private final AssistantDefinitionPort definitions;
    private final ObjectProvider<ConversationRolePort> conversationRoles;

    public PromoteDirectTaskTool(
            TaskCommandService tasks,
            AssistantDefinitionPort definitions,
            ObjectProvider<ConversationRolePort> conversationRoles) {
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.definitions = Objects.requireNonNull(definitions, "definitions 不能为空");
        this.conversationRoles =
                Objects.requireNonNull(conversationRoles, "conversationRoles 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "将本次 DIRECT execution 提升为持久 Task。适用于：语义上需要多个有序步骤；需要不同角色分工；"
                + "用户明确要求多角色协作；需要持久恢复或结构化人工交互。需要用户授权的工具直接调用即可，系统会自动升级，"
                + "不要为此调用本工具。"
                + "可选 roleKey 指定任务协调者角色（取自角色目录），可选 shape 表示形态：STEPS 拆步骤，DELEGATE 拆多角色。"
                + "普通问答、自然澄清和可在本回合完成的目标不得调用；调用后本轮立即结束，不要再输出内容。";
    }

    /** 仅改变 AAF 控制面形态，不执行外部业务动作，也不扩大工具、Role、预算或授权。 */
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
                        "reason",
                        Map.of("type", "string", "description", "需要多步骤、多角色分工、持久恢复或结构化人工交互的具体原因"),
                        "roleKey",
                        Map.of("type", "string", "description", "可选，任务协调者使用的角色，必须取自角色目录；省略则沿用当前角色"),
                        "shape",
                        Map.of(
                                "type",
                                "string",
                                "enum",
                                java.util.List.of("STEPS", "DELEGATE"),
                                "description",
                                "可选，STEPS 表示拆成有序步骤，DELEGATE 表示拆成多个不同角色的子智能体")),
                "required",
                java.util.List.of("reason"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> promote(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult promote(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null || context.nodeIdentity() != null) {
            throw new IllegalStateException("promote_to_task 仅允许 DIRECT execution 调用");
        }
        var reason = invocation.arguments().get("reason");
        if (!(reason instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("promotion reason 不能为空白");
        }
        var normalizedReason = text.trim();
        if (normalizedReason.length() > 512) {
            throw new IllegalArgumentException("promotion reason 不能超过 512 字符");
        }
        var roleKey = optionalText(invocation.arguments().get("roleKey"), "roleKey");
        var definition =
                definitions
                        .findById(context.tenantId(), context.assistantId())
                        .orElseThrow(() -> new IllegalStateException("无法读取当前助理定义"));
        if (roleKey != null) {
            if (definition.roles().stream().noneMatch(role -> role.key().equals(roleKey))) {
                throw new IllegalArgumentException(
                        "角色不在当前助理角色目录："
                                + roleKey
                                + "，候选："
                                + definition.roles().stream().map(role -> role.key()).toList());
            }
        }
        var shape = optionalText(invocation.arguments().get("shape"), "shape");
        if (shape != null && !SHAPES.contains(shape)) {
            throw new IllegalArgumentException("shape 只能是 STEPS 或 DELEGATE");
        }
        var task =
                tasks.promoteDirect(
                        context.tenantId(),
                        context.executionId(),
                        normalizedReason,
                        roleKey,
                        shape,
                        definition,
                        null);
        var port = conversationRoles.getIfAvailable();
        if (roleKey != null && port != null && context.conversationId() != null) {
            port.save(context.tenantId(), context.conversationId(), roleKey);
            log.info(
                    "[会话角色] 已记录：conversationId={}，roleKey={}，来源={}",
                    context.conversationId().value(),
                    roleKey,
                    TOOL_NAME);
        }
        var values = new LinkedHashMap<String, Object>();
        values.put("taskId", task.taskId().value());
        values.put("taskStatus", task.status().name());
        values.put("originExecutionId", context.executionId().value());
        // 控制交接：工具包装层据此挂起循环，不再发起下一次模型调用
        values.put(ToolGatewayPort.CONTROL_HANDOFF_KEY, true);
        return new ToolInvocationResult(
                "当前目标已提升为持久 Task，后续由 Task Owner Assistant 继续规划。本轮立即结束。", values);
    }

    private static String optionalText(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(field + " 必须是字符串");
        }
        return text.isBlank() ? null : text.trim();
    }
}
