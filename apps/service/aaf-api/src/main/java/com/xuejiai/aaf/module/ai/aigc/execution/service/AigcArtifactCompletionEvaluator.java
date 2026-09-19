package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.ArtifactCompletionEvaluator;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.Evaluation;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.InvocationReceipt;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.OutputContract;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.PartialFailurePolicy;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcObjectVersion;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcObjectVersionRepository;

import lombok.RequiredArgsConstructor;

/** 只读取 AIGC Run 与 Project candidate 权威事实，不在 canonical Task 内复制专业状态机。 */
@Component
@RequiredArgsConstructor
public class AigcArtifactCompletionEvaluator implements ArtifactCompletionEvaluator {

    private final AigcExecutionRunRepository runRepository;
    private final AigcObjectVersionRepository objectVersionRepository;

    @Override
    @Transactional(readOnly = true)
    public Evaluation evaluate(InvocationReceipt receipt) {
        var blockers = new ArrayList<String>();
        var runsById = new LinkedHashMap<Long, AigcExecutionRun>();
        runRepository
                .findAllById(receipt.executionRunIds())
                .forEach(run -> runsById.put(run.getId(), run));
        for (var runId : receipt.executionRunIds()) {
            var run = runsById.get(runId);
            if (run == null) {
                blockers.add("AIGC Run 不存在: " + runId);
            } else if (!receipt.projectId().equals(run.getProjectId())) {
                blockers.add("AIGC Run 项目不一致: " + runId);
            }
        }
        if (!blockers.isEmpty()) {
            return new Evaluation(false, false, blockers);
        }

        var active =
                runsById.values().stream()
                        .filter(run -> run.getStatus() == null || run.getStatus().isActive())
                        .map(AigcExecutionRun::getId)
                        .toList();
        if (!active.isEmpty()) {
            return new Evaluation(false, false, List.of("AIGC Run 尚未终态: " + active));
        }

        var candidates = new ArrayList<AigcObjectVersion>();
        receipt.executionRunIds().stream()
                .map(objectVersionRepository::findByExecutionRunIdOrderByIdAsc)
                .forEach(candidates::addAll);
        for (Map.Entry<Long, OutputContract> expected :
                receipt.completionCriteria().expectedObjects().entrySet()) {
            var matched =
                    candidates.stream()
                            .anyMatch(
                                    candidate ->
                                            receipt.projectId().equals(candidate.getProjectId())
                                                    && expected.getKey()
                                                            .equals(candidate.getObjectId())
                                                    && usableCandidate(candidate)
                                                    && satisfies(candidate, expected.getValue()));
            if (!matched) {
                blockers.add(
                        "项目对象缺少 "
                                + expected.getValue().name()
                                + " candidate: "
                                + expected.getKey());
            }
        }

        var unsuccessful =
                runsById.values().stream()
                        .filter(run -> run.getStatus() != AigcExecutionRunStatus.SUCCEEDED)
                        .map(run -> run.getId() + ":" + run.getStatus())
                        .toList();
        if (blockers.isEmpty()
                && (unsuccessful.isEmpty()
                        || receipt.completionCriteria().failurePolicy()
                                == PartialFailurePolicy.ACCEPT_COMPLETED)) {
            return new Evaluation(true, false, List.of());
        }
        if (!unsuccessful.isEmpty()) {
            blockers.add("AIGC Run 未全部成功: " + unsuccessful);
            if (receipt.completionCriteria().failurePolicy()
                    != PartialFailurePolicy.PAUSE_FOR_DECISION) {
                return new Evaluation(false, true, blockers);
            }
        }
        return new Evaluation(false, false, blockers);
    }

    private boolean usableCandidate(AigcObjectVersion candidate) {
        return "candidate".equals(candidate.getStatus()) || "adopted".equals(candidate.getStatus());
    }

    private boolean satisfies(AigcObjectVersion candidate, OutputContract contract) {
        return switch (contract) {
            case INLINE_OBJECT_VERSION, COMPOSITE_MANIFEST ->
                    candidate.getContentPayload() != null
                            && !candidate.getContentPayload().isEmpty();
            case DOCUMENT_VERSION -> candidate.getDocumentVersionId() != null;
            case MEDIA_VERSION -> hasMediaVersions(candidate.getContentPayload());
            case REFERENCE_ONLY -> true;
        };
    }

    private boolean hasMediaVersions(Map<String, Object> content) {
        if (content == null || !(content.get("mediaVersionIds") instanceof List<?> values)) {
            return false;
        }
        return values.stream().anyMatch(Number.class::isInstance);
    }
}
