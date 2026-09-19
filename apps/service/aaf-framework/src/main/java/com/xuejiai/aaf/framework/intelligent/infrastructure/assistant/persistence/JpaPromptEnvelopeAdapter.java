package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.port.PromptEnvelopePort;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelope;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelopeBody;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/** execution 级追加式信封存储；序号在事务内分配，唯一约束兜底并发。正文与信封同事务写入、分表存放。 */
public class JpaPromptEnvelopeAdapter implements PromptEnvelopePort {

    private final PromptEnvelopeRepository repository;
    private final PromptEnvelopeBodyRepository bodyRepository;

    public JpaPromptEnvelopeAdapter(
            PromptEnvelopeRepository repository, PromptEnvelopeBodyRepository bodyRepository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.bodyRepository = Objects.requireNonNull(bodyRepository, "bodyRepository 不能为空");
    }

    @Override
    @Transactional
    public PromptEnvelope append(PromptEnvelope.Draft draft, PromptEnvelopeBody body) {
        Objects.requireNonNull(draft, "draft 不能为空");
        Objects.requireNonNull(body, "body 不能为空");
        var tenantId = draft.tenantId().value();
        var executionId = draft.executionId().value();
        var envelope = draft.withSeq(repository.findMaxSeq(tenantId, executionId) + 1);
        if (!body.envelopeId().equals(envelope.envelopeId())) {
            throw new IllegalArgumentException("Prompt 正文与信封身份不一致");
        }
        body.requireConsistentWith(envelope);
        var entity = new PromptEnvelopeEntity();
        entity.setTenantId(tenantId);
        entity.setTaskId(envelope.taskId() == null ? null : envelope.taskId().value());
        entity.setExecutionId(executionId);
        entity.setEnvelopeSeq(envelope.envelopeSeq());
        entity.setAttemptNo(envelope.attemptNo());
        entity.setPromptSha256(envelope.promptSha256());
        entity.setSystemPromptSha256(envelope.systemPromptSha256());
        entity.setMessagesSha256(envelope.messagesSha256());
        entity.setTriggerKind(envelope.trigger().name());
        entity.setEnvelope(envelope);
        entity.setCreatedAt(envelope.createdAt());
        var bodyEntity = new PromptEnvelopeBodyEntity();
        bodyEntity.setEnvelopeId(envelope.envelopeId());
        bodyEntity.setTenantId(tenantId);
        bodyEntity.setExecutionId(executionId);
        bodyEntity.setEnvelopeSeq(envelope.envelopeSeq());
        bodyEntity.setBody(body);
        bodyEntity.setCreatedAt(envelope.createdAt());
        var saved = repository.saveAndFlush(entity).getEnvelope();
        bodyRepository.saveAndFlush(bodyEntity);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PromptEnvelopeBody> findBody(
            TenantId tenantId, ExecutionId executionId, int envelopeSeq) {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(executionId, "executionId 不能为空");
        if (envelopeSeq < 1) {
            throw new IllegalArgumentException("envelopeSeq 必须从 1 起递增");
        }
        return bodyRepository
                .findByTenantIdAndExecutionIdAndEnvelopeSeq(
                        tenantId.value(), executionId.value(), envelopeSeq)
                .map(PromptEnvelopeBodyEntity::getBody);
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
