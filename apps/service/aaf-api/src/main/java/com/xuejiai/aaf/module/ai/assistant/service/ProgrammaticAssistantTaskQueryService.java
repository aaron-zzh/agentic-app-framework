package com.xuejiai.aaf.module.ai.assistant.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.model.ArtifactCompletionContract.ArtifactCompletionEvaluator;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskMaterializationPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.TaskQueryPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.assistant.port.ProgrammaticAssistantTaskPort;
import com.xuejiai.aaf.module.ai.assistant.port.ProgrammaticAssistantTaskQueryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 程序化 Assistant Task 的只读查询实现；不依赖 Assistant 命令执行链。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgrammaticAssistantTaskQueryService implements ProgrammaticAssistantTaskQueryPort {

    private final TaskQueryPort taskQueries;
    private final TaskMaterializationPort materializations;
    private final ArtifactCompletionEvaluator artifactCompletionEvaluator;
    private final OperatorContext operatorContext;

    @Override
    public Optional<ProgrammaticAssistantTaskPort.TaskSnapshot> findTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        var identity = identity();
        var tenantId = new TenantId(identity.orgId().toString());
        var canonicalTaskId = new TaskId(taskId.trim());
        return taskQueries
                .find(tenantId, new UserId(identity.ownerId().toString()), canonicalTaskId)
                .map(
                        task -> {
                            var terminal = task.terminal();
                            return new ProgrammaticAssistantTaskPort.TaskSnapshot(
                                    canonicalTaskId.value(),
                                    task.status(),
                                    terminal,
                                    terminal
                                            ? List.of()
                                            : materializations
                                                    .findAwaitingExternalByTask(
                                                            tenantId, canonicalTaskId)
                                                    .stream()
                                                    .map(
                                                            candidate ->
                                                                    artifactCompletionEvaluator
                                                                            .evaluate(
                                                                                    candidate
                                                                                            .receipt()))
                                                    .filter(
                                                            evaluation ->
                                                                    !evaluation.completed()
                                                                            && !evaluation
                                                                                    .terminalFailure())
                                                    .flatMap(
                                                            evaluation ->
                                                                    evaluation.blockers().stream())
                                                    .distinct()
                                                    .toList());
                        });
    }

    private Identity identity() {
        var orgId = OrgContext.getCurrentOrgId();
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        if (orgId == null) {
            log.debug(
                    "[ProgrammaticTaskQuery] stage=identity_failed reason=missing_org_context workspaceId={}",
                    workspaceId);
            throw new AccessDeniedException("请求缺少组织上下文");
        }
        var ownerId = operatorContext.currentOwnerId();
        if (ownerId.isEmpty()) {
            log.debug(
                    "[ProgrammaticTaskQuery] stage=identity_failed reason=missing_authenticated_owner orgId={} workspaceId={}",
                    orgId,
                    workspaceId);
            throw new AccessDeniedException("请求未认证");
        }
        return new Identity(ownerId.orElseThrow(), orgId);
    }

    private record Identity(Long ownerId, Long orgId) {}
}
