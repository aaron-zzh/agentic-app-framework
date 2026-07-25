package com.xuejiai.aaf.framework.intelligent.infrastructure.governance.persistence;

import java.time.Instant;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.InvocationReceiptPort;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.port.ConversationLeasePort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.DelegatedTaskPort;

public final class JpaInvocationReceiptAdapter implements InvocationReceiptPort {
    private static final String PENDING = "PENDING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";

    private final ToolInvocationReceiptRepository repository;
    private final ConversationLeasePort leases;
    private final DelegatedTaskPort tasks;

    public JpaInvocationReceiptAdapter(
            ToolInvocationReceiptRepository repository,
            ConversationLeasePort leases,
            DelegatedTaskPort tasks) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.leases = Objects.requireNonNull(leases, "leases 不能为空");
        this.tasks = Objects.requireNonNull(tasks, "tasks 不能为空");
    }

    @Override
    @Transactional
    public Claim claim(ReceiptRequest request) {
        requireCurrent(request.context());
        var existing = repository.findByReceiptKey(request.receiptKey()).orElse(null);
        if (existing == null) {
            var created = new ToolInvocationReceiptEntity();
            created.setReceiptKey(request.receiptKey());
            created.setRequestDigest(request.requestDigest());
            created.setTenantId(request.context().tenantId().value());
            created.setUserId(request.context().userId().value());
            created.setTaskId(request.context().taskId().value());
            created.setExecutionId(request.context().executionId().value());
            created.setFencingToken(
                    request.context().lease() == null
                            ? 0L
                            : request.context().lease().fencingToken());
            created.setToolId(request.toolId());
            created.setActionKey(request.actionKey());
            created.setStatus(PENDING);
            created.setCreatedAt(request.requestedAt());
            created.setUpdatedAt(request.requestedAt());
            repository.saveAndFlush(created);
            return new Claim(Disposition.CLAIMED, null);
        }
        requireSame(existing, request);
        if (SUCCEEDED.equals(existing.getStatus())) {
            return new Claim(Disposition.REPLAY, existing.getResult());
        }
        if (PENDING.equals(existing.getStatus())) {
            var nextToken = request.context().lease() == null
                    ? 0L
                    : request.context().lease().fencingToken();
            if (existing.getFencingToken() >= nextToken) {
                return new Claim(Disposition.IN_PROGRESS, null);
            }
            existing.setExecutionId(request.context().executionId().value());
            existing.setFencingToken(nextToken);
            existing.setUpdatedAt(request.requestedAt());
            repository.saveAndFlush(existing);
            return new Claim(Disposition.CLAIMED, null);
        }
        existing.setStatus(PENDING);
        existing.setExecutionId(request.context().executionId().value());
        existing.setFencingToken(
                request.context().lease() == null
                        ? 0L
                        : request.context().lease().fencingToken());
        existing.setLastError(null);
        existing.setUpdatedAt(request.requestedAt());
        repository.saveAndFlush(existing);
        return new Claim(Disposition.CLAIMED, null);
    }

    @Override
    @Transactional
    public void complete(
            String receiptKey, InvocationContext context, ToolInvocationResult result, Instant at) {
        requireCurrent(context);
        var entity = requireBound(receiptKey, context);
        if (SUCCEEDED.equals(entity.getStatus())) {
            if (!Objects.equals(entity.getResult(), result)) {
                throw new IllegalStateException("同 receiptKey 工具结果不一致: " + receiptKey);
            }
            return;
        }
        if (!PENDING.equals(entity.getStatus())) {
            throw new IllegalStateException("receipt 当前不能完成: " + entity.getStatus());
        }
        entity.setStatus(SUCCEEDED);
        entity.setResult(result);
        entity.setUpdatedAt(at);
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void fail(String receiptKey, InvocationContext context, String failure, Instant at) {
        requireCurrent(context);
        var entity = requireBound(receiptKey, context);
        if (SUCCEEDED.equals(entity.getStatus())) return;
        entity.setStatus(FAILED);
        entity.setLastError(truncate(failure));
        entity.setUpdatedAt(at);
        repository.saveAndFlush(entity);
    }

    private ToolInvocationReceiptEntity requireBound(String receiptKey, InvocationContext context) {
        var entity = repository.findByReceiptKey(receiptKey)
                .orElseThrow(() -> new IllegalStateException("invocation receipt 不存在: " + receiptKey));
        var expectedToken = context.lease() == null ? 0L : context.lease().fencingToken();
        if (!entity.getTenantId().equals(context.tenantId().value())
                || !entity.getUserId().equals(context.userId().value())
                || !entity.getTaskId().equals(context.taskId().value())
                || !entity.getExecutionId().equals(context.executionId().value())
                || !entity.getFencingToken().equals(expectedToken)) {
            throw new IllegalStateException("invocation receipt 已绑定不同身份、任务或 execution");
        }
        return entity;
    }

    private void requireSame(ToolInvocationReceiptEntity entity, ReceiptRequest request) {
        var context = request.context();
        if (!entity.getRequestDigest().equals(request.requestDigest())
                || !entity.getTenantId().equals(context.tenantId().value())
                || !entity.getUserId().equals(context.userId().value())
                || !entity.getTaskId().equals(context.taskId().value())
                || !entity.getToolId().equals(request.toolId())
                || !entity.getActionKey().equals(request.actionKey())) {
            throw new IllegalStateException("receiptKey 已绑定不同工具动作: " + request.receiptKey());
        }
    }

    private void requireCurrent(InvocationContext context) {
        if (context.controlMode()
                != com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ControlMode.DELEGATED) {
            return;
        }
        if (context.lease() == null) {
            throw new IllegalStateException("DELEGATED invocation receipt 缺少 lease");
        }
        leases.requireCurrent(context.lease());
        tasks.requireAgentExecution(context);
    }

    private static String truncate(String failure) {
        var value = Objects.requireNonNullElse(failure, "工具调用失败");
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
