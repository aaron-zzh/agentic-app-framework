package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.time.Instant;
import java.util.ArrayList;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcExecutionCandidateProducedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcProjectCandidateRegisteredEvent;

import lombok.RequiredArgsConstructor;

/** 将执行产出的候选登记为 Project 聚合内部 ObjectVersion。 */
@Component
@RequiredArgsConstructor
public class AigcExecutionCandidateProducedListener {

    private final AigcProjectService projectService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCandidateProduced(AigcExecutionCandidateProducedEvent event) {
        var versionIds = new ArrayList<Long>();
        for (var candidate : event.objectCandidates()) {
            var version =
                    projectService.appendCandidate(
                            new AigcObjectVersionCandidateCommand(
                                    event.projectId(),
                                    event.projectObjectId(),
                                    event.executionRunId(),
                                    event.executionSubmissionId(),
                                    event.executionReservationId(),
                                    event.rootExecutionRunId(),
                                    candidate.contentJson(),
                                    candidate.documentVersionId(),
                                    candidate.mediaVersionIds()));
            versionIds.add(version.id());
        }
        if (event.objectCandidates().isEmpty() && !event.mediaVersionIds().isEmpty()) {
            var version =
                    projectService.appendCandidate(
                            new AigcObjectVersionCandidateCommand(
                                    event.projectId(),
                                    event.projectObjectId(),
                                    event.executionRunId(),
                                    event.executionSubmissionId(),
                                    event.executionReservationId(),
                                    event.rootExecutionRunId(),
                                    JsonUtils.toJsonString(
                                            java.util.Map.of(
                                                    "mediaVersionIds", event.mediaVersionIds())),
                                    null,
                                    event.mediaVersionIds()));
            versionIds.add(version.id());
        }
        eventPublisher.publishEvent(
                new AigcProjectCandidateRegisteredEvent(
                        event.eventId(),
                        event.executionRunId(),
                        event.projectId(),
                        versionIds,
                        Instant.now()));
    }
}
