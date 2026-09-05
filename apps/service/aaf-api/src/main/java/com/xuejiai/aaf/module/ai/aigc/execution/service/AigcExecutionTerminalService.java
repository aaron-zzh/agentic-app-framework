package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationReleaseCommand;

import lombok.RequiredArgsConstructor;

/** 汇总执行树终态并在整棵树终态后释放 Project reservation。 */
@Service
@RequiredArgsConstructor
public class AigcExecutionTerminalService {

    private final AigcExecutionRunRepository runRepository;
    private final AigcExecutionSubmissionRepository submissionRepository;
    private final AigcProjectApi projectApi;
    private final AigcActivityEventService activityEventService;

    @Transactional
    public void onRunTerminal(AigcExecutionRun terminalRun) {
        if (terminalRun.getStatus() == null || !terminalRun.getStatus().isTerminal()) {
            return;
        }
        var rootId = terminalRun.getRootExecutionRunId();
        if (rootId == null) {
            return;
        }
        var root = runRepository.findLockedById(rootId).orElse(null);
        if (root == null) {
            return;
        }
        if ("ORCHESTRATION".equals(root.getRunKind())) {
            var descendants = runRepository.findByRootExecutionRunIdOrderByIdAsc(root.getId());
            var children =
                    descendants.stream()
                            .filter(run -> !Objects.equals(run.getId(), root.getId()))
                            .toList();
            if (children.stream()
                    .anyMatch(run -> run.getStatus() == null || !run.getStatus().isTerminal())) {
                return;
            }
            var aggregate = aggregate(latestAttempts(children), root.getStatus());
            if (root.getStatus() != aggregate || root.getEndTime() == null) {
                root.setStatus(aggregate);
                root.setEndTime(LocalDateTime.now());
                root.setVersion(root.getVersion() + 1);
                runRepository.save(root);
            }
        }
        if (root.getStatus() == null || !root.getStatus().isTerminal()) {
            return;
        }
        var submission =
                submissionRepository
                        .findLockedById(root.getExecutionSubmissionId())
                        .orElse(null);
        if (submission == null || "TERMINAL".equals(submission.getStatus())) {
            return;
        }
        activityEventService.publish(
                root.getOwnerId(),
                "execution.run.terminal",
                root.getProjectId(),
                root.getId(),
                null,
                null,
                null,
                null,
                null,
                java.util.Map.of("status", root.getStatus().name()));
        projectApi.releaseExecution(
                new AigcProjectExecutionReservationReleaseCommand(
                        root.getExecutionReservationId(),
                        root.getExecutionSubmissionId(),
                        root.getId(),
                        "ROOT_TERMINAL"));
        submission.setStatus("TERMINAL");
        submission.setLastError(root.getErrorMessage());
        submissionRepository.save(submission);
    }

    private java.util.List<AigcExecutionRun> latestAttempts(
            java.util.List<AigcExecutionRun> children) {
        var latest = new java.util.LinkedHashMap<Long, AigcExecutionRun>();
        for (var child : children) {
            var lineageId =
                    child.getRetryOfExecutionRunId() == null
                            ? child.getId()
                            : child.getRetryOfExecutionRunId();
            var current = latest.get(lineageId);
            if (current == null || child.getRetryCount() > current.getRetryCount()) {
                latest.put(lineageId, child);
            }
        }
        return java.util.List.copyOf(latest.values());
    }

    private AigcExecutionRunStatus aggregate(
            java.util.List<AigcExecutionRun> children, AigcExecutionRunStatus rootStatus) {
        if (children.isEmpty()) {
            return rootStatus.isTerminal() ? rootStatus : AigcExecutionRunStatus.SUCCEEDED;
        }
        var succeeded =
                children.stream()
                        .filter(run -> run.getStatus() == AigcExecutionRunStatus.SUCCEEDED)
                        .count();
        if (succeeded == children.size()) {
            return AigcExecutionRunStatus.SUCCEEDED;
        }
        if (succeeded > 0) {
            return AigcExecutionRunStatus.PARTIALLY_SUCCEEDED;
        }
        return children.stream()
                        .anyMatch(run -> run.getStatus() == AigcExecutionRunStatus.FAILED)
                ? AigcExecutionRunStatus.FAILED
                : AigcExecutionRunStatus.CANCELED;
    }
}
