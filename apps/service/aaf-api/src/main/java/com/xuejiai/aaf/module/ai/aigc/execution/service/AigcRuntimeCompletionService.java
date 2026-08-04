package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Result;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Submission;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcExecutionCandidateProducedEvent;
import com.xuejiai.aaf.module.ai.aigc.project.event.AigcObjectCandidatePayload;

import lombok.RequiredArgsConstructor;

/** 统一跟踪 Agent/Workflow runtime 终态并回填 AIGC ExecutionRun。 */
@Component
@RequiredArgsConstructor
public class AigcRuntimeCompletionService {

    private final AigcExecutionRunRepository runRepository;
    private final PermissionExecutionService permissionExecutionService;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationEventPublisher eventPublisher;

    public void track(AigcActionContext context, Submission submission) {
        var run = context.executionRun();
        var output =
                run.getOutputPayload() == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<>(run.getOutputPayload());
        output.put("runtimeTraceId", submission.runtimeTraceId());
        output.put("runtimeRunId", submission.runtimeRunId());
        run.setOutputPayload(output);
        run.setStatus("running");
        run.setStartTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        runRepository.save(run);

        var attachCompletion =
                (Runnable)
                        () ->
                                submission
                                        .completion()
                                        .whenComplete(
                                                (result, failure) ->
                                                        complete(context, result, failure));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            attachCompletion.run();
                        }
                    });
        } else {
            attachCompletion.run();
        }
    }

    private void complete(AigcActionContext context, Result result, Throwable failure) {
        var project = context.project();
        permissionExecutionService.runAsOwner(
                project.userId(),
                "aigc-runtime-completion",
                () ->
                        withOrgContext(
                                project.orgId(),
                                project.workspaceId(),
                                () ->
                                        new TransactionTemplate(transactionManager)
                                                .executeWithoutResult(
                                                        ignored ->
                                                                persistCompletion(
                                                                        context, result,
                                                                        failure))));
    }

    private void persistCompletion(AigcActionContext context, Result result, Throwable failure) {
        var run = runRepository.findById(context.executionRun().getId()).orElse(null);
        if (run == null || !"running".equals(run.getStatus())) {
            return;
        }
        if (failure != null) {
            var root = unwrap(failure);
            run.setStatus("failed");
            run.setErrorMessage(root.getMessage() == null ? "runtime 执行失败" : root.getMessage());
            run.setEndTime(LocalDateTime.now());
            run.setVersion(run.getVersion() + 1);
            runRepository.save(run);
            return;
        }

        var output =
                run.getOutputPayload() == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<>(run.getOutputPayload());
        output.put("output", result.output());
        output.putAll(result.metadata());
        output.put("candidateProduced", true);
        run.setOutputPayload(output);
        run.setVersion(run.getVersion() + 1);
        runRepository.save(run);
        eventPublisher.publishEvent(
                new AigcExecutionCandidateProducedEvent(
                        java.util.UUID.randomUUID(),
                        run.getId(),
                        run.getProjectId(),
                        run.getObjectId(),
                        List.of(
                                new AigcObjectCandidatePayload(
                                        JsonUtils.toJsonString(
                                                java.util.Map.of(
                                                        "text", result.output(),
                                                        "actionKey", run.getActionKey())),
                                        null,
                                        List.of())),
                        List.of(),
                        Instant.now()));
    }

    private Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void withOrgContext(Long orgId, Long workspaceId, Runnable action) {
        var previousOrgId = OrgContext.getCurrentOrgId();
        var previousWorkspaceId = OrgContext.getCurrentWorkspaceId();
        try {
            OrgContext.setCurrentOrgId(orgId);
            OrgContext.setCurrentWorkspaceId(workspaceId);
            action.run();
        } finally {
            OrgContext.setCurrentOrgId(previousOrgId);
            OrgContext.setCurrentWorkspaceId(previousWorkspaceId);
        }
    }
}
