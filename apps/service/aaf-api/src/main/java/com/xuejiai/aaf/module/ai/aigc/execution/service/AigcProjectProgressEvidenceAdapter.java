package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.engine.credit.CreditTransactionView;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectProgressEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskApi;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskProgressView;

import lombok.RequiredArgsConstructor;

/** 由 Execution、Task 与积分 API 组装项目进度证据，不复制底层状态。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcProjectProgressEvidenceAdapter implements AigcProjectProgressEvidencePort {

    private final AigcExecutionRunRepository runRepository;
    private final AigcTaskApi taskApi;
    private final CreditService creditService;

    @Override
    public List<ApprovedTargetRunRef> approvedTargetRuns(
            Long projectId, String proposalId, Integer proposalVersion) {
        if (projectId == null || proposalId == null || proposalVersion == null) {
            return List.of();
        }
        return runRepository
                .findApprovedTargetRuns(projectId, proposalId, String.valueOf(proposalVersion))
                .stream()
                .filter(row -> row.getItemKey() != null)
                .map(
                        row ->
                                new ApprovedTargetRunRef(
                                        row.getItemKey(), row.getExecutionRunId(), row.getStatus()))
                .toList();
    }

    @Override
    public List<ApprovedTargetRunRef> latestTargetRuns(Long projectId) {
        if (projectId == null) {
            return List.of();
        }
        return runRepository.findLatestTargetRuns(projectId).stream()
                .filter(row -> row.getItemKey() != null)
                .map(
                        row ->
                                new ApprovedTargetRunRef(
                                        row.getItemKey(), row.getExecutionRunId(), row.getStatus()))
                .toList();
    }

    @Override
    public Evidence load(Long projectId, Long userId) {
        var runs = runRepository.findByProjectIdOrderByIdAsc(projectId);
        var tasks = taskApi.findProjectProgress(projectId, userId);
        var tasksByRun =
                tasks.stream()
                        .filter(task -> task.executionRunId() != null)
                        .collect(Collectors.groupingBy(AigcTaskProgressView::executionRunId));
        var txIds =
                tasks.stream()
                        .map(AigcTaskProgressView::creditTxId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        var transactions = creditService.findTransactions(userId, txIds);
        var refunds = creditService.findRefunds(userId, txIds);
        var settledById =
                transactions.stream()
                        .filter(transaction -> "SPEND".equals(transaction.type()))
                        .collect(Collectors.toMap(CreditTransactionView::id, Function.identity()));
        var unknownTaskIds =
                tasks.stream()
                        .filter(task -> terminalSuccess(task.status()))
                        .filter(
                                task ->
                                        task.creditTxId() == null
                                                || !settledById.containsKey(task.creditTxId()))
                        .map(AigcTaskProgressView::id)
                        .toList();
        var estimated = runs.stream().mapToLong(this::estimatedCredits).sum();
        var reserved = runs.stream().mapToLong(this::reservedCredits).sum();
        var settled = settledById.values().stream().mapToLong(CreditTransactionView::amount).sum();
        var refunded = refunds.stream().mapToLong(CreditTransactionView::amount).sum();
        var executionRefs =
                runs.stream()
                        .map(
                                run ->
                                        toExecutionRef(
                                                run,
                                                tasksByRun.getOrDefault(run.getId(), List.of())))
                        .toList();
        var creditsByObject = objectCreditSummaries(runs, tasksByRun, settledById, refunds);
        return new Evidence(
                executionRefs,
                new ActualCreditSummary(
                        estimated,
                        reserved,
                        settled,
                        refunded,
                        settled - refunded,
                        unknownTaskIds.isEmpty() ? 0L : null,
                        unknownTaskIds),
                creditsByObject);
    }

    /** 按 {@code AigcExecutionRun.objectId} 分组聚合 settled/refunded，退款只在原交易归属的对象口径内扣除。 */
    private java.util.Map<Long, ActualCreditSummary> objectCreditSummaries(
            List<AigcExecutionRun> runs,
            java.util.Map<Long, List<AigcTaskProgressView>> tasksByRun,
            java.util.Map<Long, CreditTransactionView> settledById,
            List<CreditTransactionView> refunds) {
        var refundedByOriginalTxId =
                refunds.stream()
                        .filter(refund -> refund.bizId() != null)
                        .collect(
                                Collectors.groupingBy(
                                        refund -> Long.valueOf(refund.bizId()),
                                        Collectors.summingLong(CreditTransactionView::amount)));
        var runsByObject =
                runs.stream()
                        .filter(run -> run.getObjectId() != null)
                        .collect(Collectors.groupingBy(AigcExecutionRun::getObjectId));
        var result = new java.util.LinkedHashMap<Long, ActualCreditSummary>();
        runsByObject.forEach(
                (objectId, objectRuns) -> {
                    var objectEstimated =
                            objectRuns.stream().mapToLong(this::estimatedCredits).sum();
                    var objectReserved = objectRuns.stream().mapToLong(this::reservedCredits).sum();
                    var objectSettledTxIds =
                            objectRuns.stream()
                                    .flatMap(
                                            run ->
                                                    tasksByRun
                                                            .getOrDefault(run.getId(), List.of())
                                                            .stream())
                                    .map(AigcTaskProgressView::creditTxId)
                                    .filter(Objects::nonNull)
                                    .filter(settledById::containsKey)
                                    .distinct()
                                    .toList();
                    var objectSettled =
                            objectSettledTxIds.stream()
                                    .mapToLong(txId -> settledById.get(txId).amount())
                                    .sum();
                    var objectRefunded =
                            objectSettledTxIds.stream()
                                    .mapToLong(
                                            txId -> refundedByOriginalTxId.getOrDefault(txId, 0L))
                                    .sum();
                    result.put(
                            objectId,
                            new ActualCreditSummary(
                                    objectEstimated,
                                    objectReserved,
                                    objectSettled,
                                    objectRefunded,
                                    objectSettled - objectRefunded,
                                    0L,
                                    List.of()));
                });
        return java.util.Map.copyOf(result);
    }

    private ExecutionProgressRef toExecutionRef(
            AigcExecutionRun run, Collection<AigcTaskProgressView> tasks) {
        return new ExecutionProgressRef(
                run.getId(),
                run.getObjectId(),
                run.getActionKey(),
                run.getTargetType(),
                run.getStatus().name(),
                tasks.stream().map(this::toTaskRef).toList(),
                estimatedCredits(run),
                reservedCredits(run),
                run.getErrorMessage());
    }

    private TaskProgressRef toTaskRef(AigcTaskProgressView task) {
        return new TaskProgressRef(
                task.id(),
                task.executionRunId(),
                task.taskType(),
                task.status(),
                task.outputMediaVersionId(),
                task.creditTxId(),
                task.failureReason());
    }

    private long estimatedCredits(AigcExecutionRun run) {
        return run.getCostCredits() == null ? 0L : run.getCostCredits().longValue();
    }

    private long reservedCredits(AigcExecutionRun run) {
        return run.getStatus().isActive() ? estimatedCredits(run) : 0L;
    }

    private boolean terminalSuccess(String status) {
        return "SUCCESS".equals(status) || "SUCCEEDED".equals(status);
    }
}
