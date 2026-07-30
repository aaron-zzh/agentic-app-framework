package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.ai.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.ExplicitConfirmation;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.PrivacyLevel;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SubjectKind;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryManagementPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryWritePort;

/** PostgreSQL/PgVector Cognition 记忆适配器。 */
public final class JpaCognitionMemoryAdapter
        implements MemoryRecallPort, MemoryWritePort, MemoryManagementPort {

    private final CognitionMemoryRepository repository;
    private final EmbeddingService embeddings;
    private final String embeddingModelId;

    public JpaCognitionMemoryAdapter(
            CognitionMemoryRepository repository,
            EmbeddingService embeddings,
            String embeddingModelId) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings 不能为空");
        this.embeddingModelId = Objects.requireNonNull(embeddingModelId, "embeddingModelId 不能为空");
        if (embeddingModelId.isBlank()) {
            throw new IllegalArgumentException("embeddingModelId 不能为空白");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryRecord> recall(RecallQuery query) {
        var subject = query.subject();
        var entities =
                query.query().isBlank()
                        ? repository.findActive(
                                subject.tenantId().value(),
                                subject.kind().name(),
                                subject.subjectId(),
                                query.at())
                        : repository.search(
                                subject.tenantId().value(),
                                subject.kind().name(),
                                subject.subjectId(),
                                vector(embeddings.embed(query.query(), embeddingModelId)),
                                query.maxItems(),
                                query.at());
        var result = new ArrayList<MemoryRecord>();
        var used = 0;
        for (var entity : entities) {
            if (result.size() >= query.maxItems()) break;
            var memory = toDomain(entity);
            if (used + memory.redactedSummary().length() > query.characterBudget()) break;
            result.add(memory);
            used += memory.redactedSummary().length();
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public MemoryManagementPort.MemoryPage list(
            MemorySubject subject, String scope, int offset, int limit, Instant at) {
        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException("记忆分页参数非法");
        }
        var entities =
                repository.listManaged(
                        subject.tenantId().value(),
                        subject.kind().name(),
                        subject.subjectId(),
                        scopeTag(scope),
                        limit,
                        offset,
                        at);
        var total =
                repository.countManaged(
                        subject.tenantId().value(),
                        subject.kind().name(),
                        subject.subjectId(),
                        scopeTag(scope),
                        at);
        return new MemoryManagementPort.MemoryPage(
                entities.stream().map(this::toDomain).toList(), total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryRecord> search(
            MemorySubject subject,
            String keyword,
            String scope,
            int limit,
            Instant at) {
        if (limit <= 0) throw new IllegalArgumentException("记忆搜索数量必须为正数");
        return repository
                .searchManaged(
                        subject.tenantId().value(),
                        subject.kind().name(),
                        subject.subjectId(),
                        Objects.requireNonNullElse(keyword, ""),
                        scopeTag(scope),
                        limit,
                        at)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long count(MemorySubject subject, String scope, Instant at) {
        return repository.countManaged(
                subject.tenantId().value(),
                subject.kind().name(),
                subject.subjectId(),
                scopeTag(scope),
                at);
    }

    @Override
    @Transactional
    public int forgetScope(
            MemorySubject subject,
            String scope,
            ExplicitConfirmation confirmation,
            Instant at) {
        confirmation.requireConfirmed("按范围清空记忆");
        return repository.forgetManagedScope(
                subject.tenantId().value(),
                subject.kind().name(),
                subject.subjectId(),
                scopeTag(scope),
                at);
    }

    @Override
    @Transactional
    public List<MemoryRecord> append(List<MemoryRecord> memories) {
        return repository.saveAll(memories.stream().map(this::toEntity).toList()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public MemoryRecord correct(
            MemorySubject subject,
            String memoryId,
            MemoryRecord replacement,
            ExplicitConfirmation confirmation) {
        confirmation.requireConfirmed("记忆纠正");
        var entity = requireOwned(subject, memoryId);
        entity.setContent(replacement.content());
        entity.setRedactedSummary(replacement.redactedSummary());
        entity.setImportance(replacement.importance());
        entity.setConfidence(replacement.confidence());
        entity.setPrivacy(replacement.privacy().name());
        entity.setTags(replacement.tags());
        entity.setEmbedding(embeddings.embed(replacement.content(), embeddingModelId));
        entity.setUpdatedAt(confirmation.confirmedAt());
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public void forget(
            MemorySubject subject,
            List<String> memoryIds,
            ExplicitConfirmation confirmation,
            Instant at) {
        confirmation.requireConfirmed("记忆删除/遗忘");
        repository.forget(
                subject.tenantId().value(),
                subject.kind().name(),
                subject.subjectId(),
                memoryIds,
                at);
    }

    @Override
    @Transactional
    public int mergeVisitor(
            MemorySubject visitor,
            MemorySubject user,
            ExplicitConfirmation confirmation,
            Instant at) {
        confirmation.requireConfirmed("匿名记忆登录合并");
        if (visitor.kind() != SubjectKind.VISITOR || user.kind() != SubjectKind.USER) {
            throw new IllegalArgumentException("登录合并要求 VISITOR -> USER");
        }
        if (!visitor.tenantId().equals(user.tenantId())) {
            throw new IllegalArgumentException("不能跨 tenant 合并记忆");
        }
        return repository.mergeVisitor(
                visitor.tenantId().value(), visitor.subjectId(), user.subjectId(), at);
    }

    @Override
    @Transactional
    public void expireAnonymous(Instant at) {
        repository.expireVisitors(at);
    }

    private CognitionMemoryEntity requireOwned(MemorySubject subject, String memoryId) {
        var entity =
                repository
                        .findById(memoryId)
                        .orElseThrow(() -> new IllegalArgumentException("记忆不存在: " + memoryId));
        if (!entity.getTenantId().equals(subject.tenantId().value())
                || !entity.getSubjectKind().equals(subject.kind().name())
                || !entity.getSubjectId().equals(subject.subjectId())) {
            throw new IllegalArgumentException("记忆不存在: " + memoryId);
        }
        return entity;
    }

    private CognitionMemoryEntity toEntity(MemoryRecord memory) {
        var entity = new CognitionMemoryEntity();
        entity.setMemoryId(memory.memoryId());
        entity.setTenantId(memory.subject().tenantId().value());
        entity.setSubjectKind(memory.subject().kind().name());
        entity.setSubjectId(memory.subject().subjectId());
        entity.setContent(memory.content());
        entity.setRedactedSummary(memory.redactedSummary());
        entity.setImportance(memory.importance());
        entity.setConfidence(memory.confidence());
        entity.setPrivacy(memory.privacy().name());
        entity.setTags(memory.tags());
        entity.setEmbedding(embeddings.embed(memory.content(), embeddingModelId));
        entity.setExpiresAt(memory.expiresAt());
        entity.setCreatedAt(memory.createdAt());
        entity.setUpdatedAt(memory.createdAt());
        return entity;
    }

    private MemoryRecord toDomain(CognitionMemoryEntity entity) {
        return new MemoryRecord(
                entity.getMemoryId(),
                new MemorySubject(
                        new com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId(
                                entity.getTenantId()),
                        SubjectKind.valueOf(entity.getSubjectKind()),
                        entity.getSubjectId()),
                entity.getContent(),
                entity.getRedactedSummary(),
                entity.getImportance(),
                entity.getConfidence(),
                PrivacyLevel.valueOf(entity.getPrivacy()),
                entity.getTags(),
                entity.getExpiresAt(),
                entity.getCreatedAt());
    }

    private static String scopeTag(String scope) {
        return scope == null ? null : "scope:" + scope;
    }

    private static String vector(float[] values) {
        var builder = new StringBuilder("[");
        for (var index = 0; index < values.length; index++) {
            if (index > 0) builder.append(',');
            builder.append(values[index]);
        }
        return builder.append(']').toString();
    }
}
