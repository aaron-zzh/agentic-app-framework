package com.xuejiai.aaf.framework.intelligent.cognition.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate;
import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate.EvidenceKind;
import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate.EvidenceRef;
import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate.SourceKind;
import com.xuejiai.aaf.framework.intelligent.cognition.learning.LearningCandidate.Status;
import com.xuejiai.aaf.framework.intelligent.cognition.port.LearningCandidatePort;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.ExecutionEventPublicMapper;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

import lombok.extern.slf4j.Slf4j;

/** 被动记录和人工审核学习候选；不会自动修改 Role、Skill、Prompt、模型、授权、计划或画像。 */
@Service
@Slf4j
public final class LearningCandidateApplicationService {

    private static final int SCHEMA_VERSION = 1;

    private final LearningCandidatePort candidates;
    private final ExecutionEventPublicMapper publicEvents;

    public LearningCandidateApplicationService(
            LearningCandidatePort candidates, ExecutionEventPublicMapper publicEvents) {
        this.candidates = Objects.requireNonNull(candidates, "candidates 不能为空");
        this.publicEvents = Objects.requireNonNull(publicEvents, "publicEvents 不能为空");
    }

    /** 仅消费已经写入 ai_task_event 的事实；失败不阻断主任务，持久事实可后续重放补偿。 */
    @EventListener
    public void capture(StoredExecutionEvent source) {
        Objects.requireNonNull(source, "source 不能为空");
        if (source.event().taskId() == null || !candidateSource(source.event().type())) {
            return;
        }
        try {
            record(source, List.of());
        } catch (RuntimeException failure) {
            log.warn(
                    "[LearningCandidate] action=capture_failed taskId={} sourceEventId={} eventType={} failureType={}",
                    source.event().taskId().value(),
                    source.event().eventId().value(),
                    source.event().type(),
                    failure.getClass().getName());
        }
    }

    public LearningCandidate record(StoredExecutionEvent source, List<EvidenceRef> evidenceRefs) {
        Objects.requireNonNull(source, "source 不能为空");
        var event = source.event();
        var projected = publicEvents.map(source);
        var sourceKind = sourceKind(event.type());
        var summaryCode = projected.data().values().get("summaryCode");
        if (!(summaryCode instanceof String safeCode)) {
            throw new IllegalStateException("公共任务事件缺少安全摘要代码");
        }
        var refs =
                evidenceRefs == null || evidenceRefs.isEmpty()
                        ? List.of(new EvidenceRef(EvidenceKind.EVENT, event.eventId().value()))
                        : List.copyOf(evidenceRefs);
        var at = Instant.now();
        var candidate =
                new LearningCandidate(
                        UUID.randomUUID().toString(),
                        SCHEMA_VERSION,
                        event.tenantId(),
                        event.taskId(),
                        event.executionId(),
                        event.runId(),
                        event.sessionId(),
                        event.eventId().value(),
                        source.eventOffset(),
                        sourceKind,
                        safeCode,
                        safeSummary(sourceKind, projected.status()),
                        refs,
                        Status.DRAFT,
                        null,
                        null,
                        at,
                        at,
                        0);
        return candidates.create(candidate);
    }

    public LearningCandidate review(
            TenantId tenantId,
            String candidateId,
            long expectedRevision,
            String reviewerId,
            String decisionCode) {
        return transition(
                tenantId, candidateId, expectedRevision, Status.REVIEWED, reviewerId, decisionCode);
    }

    public LearningCandidate publish(
            TenantId tenantId,
            String candidateId,
            long expectedRevision,
            String reviewerId,
            String decisionCode) {
        return transition(
                tenantId,
                candidateId,
                expectedRevision,
                Status.PUBLISHED,
                reviewerId,
                decisionCode);
    }

    public LearningCandidate reject(
            TenantId tenantId,
            String candidateId,
            long expectedRevision,
            String reviewerId,
            String decisionCode) {
        return transition(
                tenantId, candidateId, expectedRevision, Status.REJECTED, reviewerId, decisionCode);
    }

    private LearningCandidate transition(
            TenantId tenantId,
            String candidateId,
            long expectedRevision,
            Status target,
            String reviewerId,
            String decisionCode) {
        return candidates.transition(
                tenantId,
                candidateId,
                expectedRevision,
                target,
                reviewerId,
                decisionCode,
                Instant.now());
    }

    private static SourceKind sourceKind(ExecutionEventType type) {
        return switch (type) {
            case EXECUTION_COMPLETED -> SourceKind.TASK_COMPLETED;
            case EXECUTION_FAILED, RUN_FAILED, COMMAND_REJECTED -> SourceKind.TASK_FAILED;
            case APPROVAL_REQUESTED,
                    APPROVAL_RESOLVED,
                    AUTHORIZATION_REQUESTED,
                    AUTHORIZATION_GRANTED,
                    AUTHORIZATION_DENIED,
                    AUTHORIZATION_REVOKED ->
                    SourceKind.HITL;
            case VALIDATION_STARTED, VALIDATION_COMPLETED, VALIDATION_FAILED ->
                    SourceKind.VALIDATION;
            default -> throw new IllegalArgumentException("该执行事实不能作为独立学习依据: " + type);
        };
    }

    private static boolean candidateSource(ExecutionEventType type) {
        return switch (type) {
            case EXECUTION_COMPLETED,
                    EXECUTION_FAILED,
                    RUN_FAILED,
                    COMMAND_REJECTED,
                    APPROVAL_REQUESTED,
                    APPROVAL_RESOLVED,
                    AUTHORIZATION_REQUESTED,
                    AUTHORIZATION_GRANTED,
                    AUTHORIZATION_DENIED,
                    AUTHORIZATION_REVOKED,
                    VALIDATION_STARTED,
                    VALIDATION_COMPLETED,
                    VALIDATION_FAILED ->
                    true;
            default -> false;
        };
    }

    private static String safeSummary(SourceKind sourceKind, String status) {
        return "%s_%s_SAFE_FACT".formatted(sourceKind.name(), status);
    }
}
