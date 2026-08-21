package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate;
import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate.Status;
import com.xuejiai.aaf.framework.intelligent.cognition.port.LearningCandidatePort;
import com.xuejiai.aaf.framework.intelligent.infrastructure.trace.persistence.ExecutionEventRepository;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

import lombok.extern.slf4j.Slf4j;

/** LearningCandidate JPA 适配器；日志仅记录稳定标识和状态，不记录摘要正文。 */
@Component
@Slf4j
public class JpaLearningCandidateAdapter implements LearningCandidatePort {

    private final LearningCandidateRepository repository;
    private final ExecutionEventRepository executionEvents;

    public JpaLearningCandidateAdapter(
            LearningCandidateRepository repository, ExecutionEventRepository executionEvents) {
        this.repository = repository;
        this.executionEvents = executionEvents;
    }

    @Override
    @Transactional
    public LearningCandidate create(LearningCandidate candidate) {
        requirePersistedSource(candidate);
        var existing =
                repository
                        .findByTenantIdAndSourceEventId(
                                candidate.tenantId().value(), candidate.sourceEventId())
                        .orElse(null);
        if (existing != null) {
            return existing.getCandidate();
        }
        var entity = new LearningCandidateEntity();
        entity.setCandidateId(candidate.candidateId());
        entity.setTenantId(candidate.tenantId().value());
        entity.setTaskId(candidate.taskId().value());
        entity.setExecutionId(candidate.executionId().value());
        entity.setRunId(candidate.runId().value());
        entity.setSessionId(candidate.sessionId().value());
        entity.setSourceEventId(candidate.sourceEventId());
        entity.setSourceEventOffset(candidate.sourceEventOffset());
        entity.setStatus(candidate.status().name());
        entity.setSchemaVersion(candidate.schemaVersion());
        entity.setCandidate(candidate);
        entity.setCreatedAt(candidate.createdAt());
        entity.setUpdatedAt(candidate.updatedAt());
        entity.setVersion(0L);
        repository.saveAndFlush(entity);
        log.info(
                "[LearningCandidate] action=created candidateId={} taskId={} sourceEventId={} status={}",
                candidate.candidateId(),
                candidate.taskId().value(),
                candidate.sourceEventId(),
                candidate.status());
        return candidate;
    }

    private void requirePersistedSource(LearningCandidate candidate) {
        var source =
                executionEvents
                        .findById(candidate.sourceEventId())
                        .orElseThrow(() -> new IllegalArgumentException("学习候选缺少持久化来源事件"));
        var persisted = source.getEvent();
        if (!source.getTenantId().equals(candidate.tenantId().value())
                || !source.getTaskId().equals(candidate.taskId().value())
                || !source.getExecutionId().equals(candidate.executionId().value())
                || !persisted.runId().equals(candidate.runId())
                || !persisted.sessionId().equals(candidate.sessionId())
                || !Objects.equals(source.getEventOffset(), candidate.sourceEventOffset())) {
            throw new IllegalArgumentException("学习候选与持久化来源事件边界不一致");
        }
    }

    @Override
    public Optional<LearningCandidate> find(TenantId tenantId, String candidateId) {
        return repository
                .findByTenantIdAndCandidateId(tenantId.value(), candidateId)
                .map(LearningCandidateEntity::getCandidate);
    }

    @Override
    @Transactional
    public LearningCandidate transition(
            TenantId tenantId,
            String candidateId,
            long expectedRevision,
            Status target,
            String reviewerId,
            String decisionCode,
            Instant at) {
        var entity =
                repository
                        .findByTenantIdAndCandidateId(tenantId.value(), candidateId)
                        .orElseThrow(() -> new IllegalArgumentException("LearningCandidate 不存在"));
        var current = entity.getCandidate();
        if (current.revision() != expectedRevision) {
            throw new IllegalStateException("LearningCandidate revision 已变化");
        }
        var changed = current.transition(target, reviewerId, decisionCode, at);
        entity.setStatus(changed.status().name());
        entity.setCandidate(changed);
        entity.setUpdatedAt(changed.updatedAt());
        repository.saveAndFlush(entity);
        log.info(
                "[LearningCandidate] action=transitioned candidateId={} taskId={} from={} to={} revision={}",
                changed.candidateId(),
                changed.taskId().value(),
                current.status(),
                changed.status(),
                changed.revision());
        return changed;
    }
}
