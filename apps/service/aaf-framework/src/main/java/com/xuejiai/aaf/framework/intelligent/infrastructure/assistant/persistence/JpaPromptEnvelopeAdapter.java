package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.port.PromptEnvelopePort;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelope;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** execution 级追加式信封存储；序号在事务内分配，唯一约束兜底并发。 */
public class JpaPromptEnvelopeAdapter implements PromptEnvelopePort {

    private final PromptEnvelopeRepository repository;

    public JpaPromptEnvelopeAdapter(PromptEnvelopeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    @Override
    @Transactional
    public PromptEnvelope append(PromptEnvelope.Draft draft) {
        Objects.requireNonNull(draft, "draft 不能为空");
        var tenantId = draft.tenantId().value();
        var executionId = draft.executionId().value();
        var envelope = draft.withSeq(repository.findMaxSeq(tenantId, executionId) + 1);
        var entity = new PromptEnvelopeEntity();
        entity.setTenantId(tenantId);
        entity.setTaskId(envelope.taskId().value());
        entity.setExecutionId(executionId);
        entity.setEnvelopeSeq(envelope.envelopeSeq());
        entity.setAttemptNo(envelope.attemptNo());
        entity.setPromptSha256(envelope.promptSha256());
        entity.setTriggerKind(envelope.trigger().name());
        entity.setEnvelope(envelope);
        entity.setCreatedAt(envelope.createdAt());
        return repository.saveAndFlush(entity).getEnvelope();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PromptEnvelope> findLatest(TenantId tenantId, ExecutionId executionId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        return repository
                .findFirstByTenantIdAndExecutionIdOrderByEnvelopeSeqDesc(
                        tenantId.value(), executionId.value())
                .map(PromptEnvelopeEntity::getEnvelope);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromptEnvelope> findAll(TenantId tenantId, ExecutionId executionId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        return repository
                .findByTenantIdAndExecutionIdOrderByEnvelopeSeqAsc(
                        tenantId.value(), executionId.value())
                .stream()
                .map(PromptEnvelopeEntity::getEnvelope)
                .toList();
    }
}
