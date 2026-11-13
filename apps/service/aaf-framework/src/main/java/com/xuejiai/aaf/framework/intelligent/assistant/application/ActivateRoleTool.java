package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolGatewayPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationRolePort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 轻量态协调者切换角色的唯一模型入口（ADR-011）：登记切换后当前执行立即收口，由新角色在同一条 SSE 上续接回答。
 *
 * <p>只在未锁定角色（ROUTE_AUTO）的 DIRECT 协调者可见；续接执行使用 ROUTE_FIXED，看不到本工具，因此每个 Run 最多切换一次。
 */
@Slf4j
public final class ActivateRoleTool implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "activate_role";

    private static final int MAX_REASON_LENGTH = 256;

    private final AssistantDefinitionPort definitions;
    private final DirectRoleHandoffRegistry handoffs;
    private final ObjectProvider<ConversationRolePort> conversationRoles;

    public ActivateRoleTool(
            AssistantDefinitionPort definitions,
            DirectRoleHandoffRegistry handoffs,
            ObjectProvider<ConversationRolePort> conversationRoles) {
        this.definitions = Objects.requireNonNull(definitions, "definitions 不能为空");
        this.handoffs = Objects.requireNonNull(handoffs, "handoffs 不能为空");
        this.conversationRoles =
                Objects.requireNonNull(conversationRoles, "conversationRoles 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "切换到角色目录中更合适的角色，由新角色继续回答本条消息。必须在输出任何正文之前调用；" + "调用后本轮立即结束，不要再输出内容。当前角色已能胜任时不得调用。";
    }

    /** 只改变本次回答使用的角色，不执行外部业务动作，也不扩大工具、预算或授权。 */
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
                        "roleKey",
                        Map.of("type", "string", "description", "目标角色 roleKey，必须取自角色目录"),
                        "reason",
                        Map.of("type", "string", "description", "切换理由，说明为什么目标角色更合适，不超过 256 字符")),
                "required",
                List.of("roleKey", "reason"));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> activate(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult activate(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() != null || context.nodeIdentity() != null) {
            throw new IllegalStateException("activate_role 仅允许轻量态协调者调用");
        }
        var roleKey = requireText(invocation.arguments().get("roleKey"), "roleKey");
        var reason = requireText(invocation.arguments().get("reason"), "reason");
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("reason 不能超过 " + MAX_REASON_LENGTH + " 字符");
        }
        var definition =
                definitions
                        .findById(context.tenantId(), context.assistantId())
                        .orElseThrow(() -> new IllegalStateException("无法读取当前助理定义"));
        var target =
                definition.roles().stream()
                        .filter(role -> role.key().equals(roleKey))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "角色不在当前助理角色目录："
                                                        + roleKey
                                                        + "，候选："
                                                        + definition.roles().stream()
                                                                .map(role -> role.key())
                                                                .toList()));
        var currentRoleKey =
                context.skillExecutionProfile()
                        .map(profile -> profile.selection().roleKey())
                        .orElse(null);
        if (roleKey.equals(currentRoleKey)) {
            throw new IllegalArgumentException("当前已是角色 " + roleKey + "，无需切换，请直接回答");
        }
        log.info(
                "[角色切换] 协调者请求切换：executionId={}，from={}，to={}，reason={}",
                context.executionId().value(),
                currentRoleKey,
                roleKey,
                reason);
        var registered =
                handoffs.register(
                        context.executionId(),
                        new DirectRoleHandoffRegistry.Handoff(
                                currentRoleKey,
                                target.key(),
                                target.name(),
                                reason,
                                definition.version().value()));
        if (!registered) {
            throw new IllegalStateException("本轮已切换过角色，不能再次切换");
        }
        var port = conversationRoles.getIfAvailable();
        if (port != null && context.conversationId() != null) {
            port.save(context.tenantId(), context.conversationId(), target.key());
            log.info(
                    "[会话角色] 已记录：conversationId={}，roleKey={}，来源={}",
                    context.conversationId().value(),
                    target.key(),
                    TOOL_NAME);
        }
        var values = new LinkedHashMap<String, Object>();
        if (currentRoleKey != null) {
            values.put("fromRoleKey", currentRoleKey);
        }
        values.put("toRoleKey", target.key());
        values.put("toRoleName", target.name());
        // 控制交接：工具包装层据此挂起循环，不再发起下一次模型调用
        values.put(ToolGatewayPort.CONTROL_HANDOFF_KEY, true);
        return new ToolInvocationResult("已切换到角色 " + target.name() + "，本轮立即结束，由新角色继续回答。", values);
    }

    private static String requireText(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return text.trim();
    }
}
