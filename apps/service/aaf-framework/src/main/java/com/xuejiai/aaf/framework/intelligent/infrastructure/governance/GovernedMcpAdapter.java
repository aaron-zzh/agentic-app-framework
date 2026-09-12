package com.xuejiai.aaf.framework.intelligent.infrastructure.governance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import com.xuejiai.aaf.framework.intelligent.agent.model.ToolRef;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ConnectorActionPort.ConnectorAction;
import com.xuejiai.aaf.framework.intelligent.agent.port.InvocationReceiptPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.InvocationReceiptPort.Disposition;
import com.xuejiai.aaf.framework.intelligent.agent.port.InvocationReceiptPort.ReceiptRequest;
import com.xuejiai.aaf.framework.intelligent.agent.port.McpPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskUnitOfWork;

import reactor.core.publisher.Mono;

/** MCP 调用适配器；凭据句柄为受信上下文，业务参数强制注入 task namespace。 */
public final class GovernedMcpAdapter implements McpPort {
    private final ConnectorActionPort connectors;
    private final ConversationLeasePort leases;
    private final TaskUnitOfWork tasks;
    private final InvocationReceiptPort receipts;

    public GovernedMcpAdapter(
            ConnectorActionPort connectors,
            ConversationLeasePort leases,
            TaskUnitOfWork tasks,
            InvocationReceiptPort receipts) {
        this.connectors = Objects.requireNonNull(connectors, "connectors 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
        this.receipts = Objects.requireNonNull(receipts, "receipts 不能为空");
    }

    @Override
    public Mono<ToolInvocationResult> invoke(McpInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() == null) {
            return Mono.error(
                    new IllegalStateException("DIRECT execution 不得调用 MCP，必须先 promotion 为 Task"));
        }
        requireCurrent(context);
        tasks.recordSideEffectIntent(context.tenantId(), context.executionId(), Instant.now());
        var arguments = new LinkedHashMap<>(invocation.businessArguments());
        arguments.put(
                "namespace",
                "tenant="
                        + context.tenantId().value()
                        + "/user="
                        + context.userId().value()
                        + "/task="
                        + context.taskId().value());
        var toolId = "mcp:" + invocation.serverId() + ':' + invocation.toolName();
        var receiptKey =
                "tool:"
                        + sha256(
                                String.join(
                                        "|",
                                        context.tenantId().value(),
                                        context.userId().value(),
                                        context.taskId().value(),
                                        toolId,
                                        invocation.idempotencyKey()));
        var claim =
                receipts.claim(
                        new ReceiptRequest(
                                receiptKey,
                                sha256(new TreeMap<>(arguments).toString()),
                                toolId,
                                invocation.idempotencyKey(),
                                context,
                                Instant.now()));
        if (claim.disposition() == Disposition.REPLAY) {
            return Mono.just(claim.existingResult());
        }
        if (claim.disposition() == Disposition.IN_PROGRESS) {
            return Mono.error(new IllegalStateException("同一 MCP 动作正在执行"));
        }
        tasks.reserveToolCall(context, invocation.toolName(), Instant.now());
        var tool = new ToolRef(invocation.toolName(), 1, invocation.toolName());
        return connectors
                .invoke(
                        new ConnectorAction(
                                invocation.idempotencyKey(),
                                tool,
                                invocation.serverId(),
                                invocation.credentialHandle(),
                                Set.of(),
                                arguments,
                                receiptKey,
                                true,
                                context))
                .map(result -> complete(invocation, receiptKey, result))
                .onErrorResume(
                        failure -> {
                            if (!(failure instanceof TaskUnitOfWork.BudgetExceededException)) {
                                receipts.fail(
                                        receiptKey, context, failure.getMessage(), Instant.now());
                            }
                            return Mono.error(failure);
                        });
    }

    private ToolInvocationResult complete(
            McpInvocation invocation, String receiptKey, ToolInvocationResult result) {
        var context = invocation.context();
        requireCurrent(context);
        receipts.complete(receiptKey, context, result, Instant.now());
        tasks.recordToolUsage(
                context,
                longMetadata(result, "toolUnits", 1),
                decimalMetadata(result, "credits"),
                Instant.now());
        return result;
    }

    private void requireCurrent(
            com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext context) {
        if (context.lease() == null) throw new IllegalStateException("MCP 调用缺少 conversation lease");
        leases.requireCurrent(context.lease());
        tasks.requireAgentExecution(context);
    }

    private static long longMetadata(ToolInvocationResult result, String key, long fallback) {
        var value = result.metadata().get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static BigDecimal decimalMetadata(ToolInvocationResult result, String key) {
        var value = result.metadata().get(key);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256", failure);
        }
    }
}
