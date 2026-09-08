package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcChildActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionApi;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunTreeView;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunView;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeCancellationPort;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcActionOptionVO;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationBindCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationReleaseCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskApi;

import lombok.RequiredArgsConstructor;

/** AIGC 动作命令服务，统一创建并推进 ExecutionRun。 */
@Service
@RequiredArgsConstructor
public class AigcActionCommandService implements AigcExecutionApi {

    private static final Map<String, String> ACTION_LABELS =
            Map.ofEntries(
                    Map.entry("brief.refine", "完善简报"),
                    Map.entry("concept.generate", "生成创意方向"),
                    Map.entry("copy.generate", "生成文案"),
                    Map.entry("image.generate", "生成图片"),
                    Map.entry("image.edit", "局部修改图片"),
                    Map.entry("video.generate", "生成视频"),
                    Map.entry("deliverable.regenerate", "重新生成交付物"),
                    Map.entry("package.generate", "批量生成交付包"),
                    Map.entry("outline.generate", "生成文章提纲"),
                    Map.entry("article.draft", "撰写文章草稿"),
                    Map.entry("article.rewrite", "改写文章"),
                    Map.entry("article.seo_optimize", "优化文章 SEO"));

    private final AigcProjectApi projectApi;
    private final AigcExecutionBindingResolver bindingResolver;
    private final AigcExecutionRunRepository runRepository;
    private final AigcExecutionSubmissionRepository submissionRepository;
    private final AigcExecutionSubmissionService submissionService;
    private final AigcExecutionTerminalService terminalService;
    private final AigcExecutionTaskRefRepository taskRefRepository;
    private final AigcTaskApi taskApi;
    private final AigcMediaApi mediaApi;
    private final AigcRuntimeCancellationPort runtimeCancellationPort;
    private final OperatorContext operatorContext;
    private final List<AigcActionExecutor> executors;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_PROJECT_READ)
    public List<AigcActionOptionVO> listActions(Long projectId) {
        var project = requireWritableProject(projectId, null);
        return ACTION_LABELS.keySet().stream()
                .map(actionKey -> toOption(project, actionKey))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_PROJECT_ACTION)
    public AigcExecutionRunView submit(AigcActionCommand command) {
        return prepareSubmission(command);
    }

    @Override
    @Transactional
    public AigcExecutionRunView submitDeferred(AigcActionCommand command) {
        return prepareSubmission(command);
    }

    @Transactional
    public void failRecovery(Long submissionId, String errorMessage) {
        var submission =
                submissionRepository
                        .findLockedById(submissionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行 submission 不存在"));
        if ("TERMINAL".equals(submission.getStatus())
                || "RECOVERY_FAILED".equals(submission.getStatus())) {
            return;
        }
        var reason =
                errorMessage == null || errorMessage.isBlank()
                        ? "execution submission 恢复失败"
                        : errorMessage;
        if (submission.getRootExecutionRunId() == null) {
            if (submission.getReservationId() != null) {
                projectApi.releaseExecution(
                        new AigcProjectExecutionReservationReleaseCommand(
                                submission.getReservationId(),
                                submission.getId(),
                                null,
                                "PRE_BIND_CANCEL"));
            }
        } else {
            var tree =
                    runRepository.findByRootExecutionRunIdOrderByIdAsc(
                            submission.getRootExecutionRunId());
            var preBind =
                    tree.stream()
                            .anyMatch(
                                    run ->
                                            Objects.equals(
                                                            run.getId(),
                                                            submission.getRootExecutionRunId())
                                                    && run.getStatus()
                                                            == AigcExecutionRunStatus.PENDING_BIND);
            tree.stream()
                    .sorted(java.util.Comparator.comparing(AigcExecutionRun::getId).reversed())
                    .filter(run -> run.getStatus().isActive())
                    .forEach(run -> cancelRun(run, reason));
            var root =
                    runRepository
                            .findLockedById(submission.getRootExecutionRunId())
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    GlobalErrorCode.NOT_FOUND, "执行根 Run 不存在"));
            root.setStatus(AigcExecutionRunStatus.FAILED);
            root.setErrorMessage(reason);
            root.setEndTime(LocalDateTime.now());
            root.setVersion(root.getVersion() + 1);
            runRepository.save(root);
            if (preBind) {
                projectApi.releaseExecution(
                        new AigcProjectExecutionReservationReleaseCommand(
                                root.getExecutionReservationId(),
                                root.getExecutionSubmissionId(),
                                null,
                                "PRE_BIND_CANCEL"));
            } else {
                terminalService.onRunTerminal(root);
            }
        }
        submission.setStatus("RECOVERY_FAILED");
        submission.setLastError(reason);
        submissionRepository.save(submission);
    }

    @Transactional
    public void resumeSubmission(Long submissionId, AigcActionCommand command) {
        requireWritableProject(command.projectId(), command.actionKey());
        var submission =
                submissionRepository
                        .findLockedById(submissionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行 submission 不存在"));
        if (submission.getRootExecutionRunId() == null) {
            prepareSubmission(command);
            return;
        }
        var run =
                runRepository
                        .findLockedById(submission.getRootExecutionRunId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行根 Run 不存在"));
        if (AigcExecutionRunStatus.PENDING_BIND.equals(run.getStatus())) {
            projectApi.bindExecution(
                    new AigcProjectExecutionReservationBindCommand(
                            run.getExecutionReservationId(), submission.getId(), run.getId()));
            run.setStatus(AigcExecutionRunStatus.PENDING);
            runRepository.save(run);
            submission.setStatus("BOUND");
            submissionRepository.save(submission);
        }
        if (AigcExecutionRunStatus.PENDING.equals(run.getStatus())
                && "BOUND".equals(submission.getStatus())) {
            if ("project.cover.generate".equals(command.actionKey())) {
                projectApi.markCoverExecutionStarted(command.projectId(), run.getId());
            }
            publishDispatch(run, command);
        }
    }

    private AigcExecutionRunView prepareSubmission(AigcActionCommand command) {
        var receipt = submissionService.record(command);
        var project = requireWritableProject(command.projectId(), command.actionKey());
        var submission =
                submissionRepository
                        .findLockedById(receipt.submission().getId())
                        .filter(candidate -> Objects.equals(candidate.getProjectId(), project.id()))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行 submission 不存在"));
        var existingRoot =
                runRepository.findByExecutionSubmissionIdAndParentExecutionRunIdIsNull(
                        submission.getId());
        if (submission.getRootExecutionRunId() == null && existingRoot.isPresent()) {
            submission.setRootExecutionRunId(existingRoot.get().getId());
            submission.setReservationId(existingRoot.get().getExecutionReservationId());
            submission.setStatus(
                    existingRoot.get().getStatus() == AigcExecutionRunStatus.PENDING_BIND
                            ? "ROOT_CREATED"
                            : "BOUND");
            submissionRepository.save(submission);
        }
        if (submission.getRootExecutionRunId() != null) {
            var existing =
                    runRepository
                            .findById(submission.getRootExecutionRunId())
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    GlobalErrorCode.NOT_FOUND, "执行根 Run 不存在"));
            if (AigcExecutionRunStatus.PENDING_BIND.equals(existing.getStatus())
                    || (AigcExecutionRunStatus.PENDING.equals(existing.getStatus())
                            && "BOUND".equals(submission.getStatus()))) {
                resumeSubmission(submission.getId(), command);
            }
            return toApiView(runRepository.findById(existing.getId()).orElse(existing));
        }
        if ("INTENT_RECORDED".equals(submission.getStatus())) {
            if (submissionRepository.claimPreparing(submission.getId()) != 1) {
                throw new BusinessException(409, "动作 submission 已被其他 owner 认领");
            }
            submission.setStatus("PREPARING");
        } else if (!java.util.Set.of("PREPARING", "RESERVED").contains(submission.getStatus())) {
            throw new BusinessException(409, "动作 submission 当前状态不可准备");
        }
        command.attachmentMediaVersionIds()
                .forEach(
                        mediaVersionId ->
                                mediaApi.getByVersionId(mediaVersionId, project.userId()));
        if ("project.cover.generate".equals(command.actionKey())) {
            supersedeCoverRuns(command.projectId(), "已提交新的封面生成请求");
        }
        var object = resolveObject(project, command);
        var binding = bindingResolver.resolve(project, command.actionKey());
        validateBudget(project, binding);
        if (Boolean.TRUE.equals(binding.getConfirmationRequired()) && !command.confirmed()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "动作需要确认后执行");
        }
        var currentGraphRevision = projectApi.getGraph(project.id()).revisionNo();
        if (command.expectedGraphRevision() != null
                && !command.expectedGraphRevision().equals(currentGraphRevision)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目图谱版本已变化");
        }
        var reservation =
                projectApi.reserveExecution(
                        new AigcProjectExecutionReservationCommand(
                                submission.getId(),
                                project.id(),
                                object == null ? null : object.id(),
                                command.actionKey(),
                                command.selectedProjectObjectIds(),
                                command.expectedGraphRevision() == null
                                        ? currentGraphRevision
                                        : command.expectedGraphRevision(),
                                command.idempotencyKey()));
        submission.setReservationId(reservation.id());
        submission.setStatus("RESERVED");
        submissionRepository.save(submission);
        var run =
                runRepository
                        .findByExecutionSubmissionIdAndParentExecutionRunIdIsNull(
                                submission.getId())
                        .orElseGet(
                                () ->
                                        createRootRun(
                                                project,
                                                object,
                                                binding,
                                                command,
                                                submission.getId(),
                                                reservation));
        submission.setRootExecutionRunId(run.getId());
        submission.setStatus("ROOT_CREATED");
        submissionRepository.save(submission);
        projectApi.bindExecution(
                new AigcProjectExecutionReservationBindCommand(
                        reservation.id(), submission.getId(), run.getId()));
        run.setStatus(AigcExecutionRunStatus.PENDING);
        runRepository.save(run);
        submission.setStatus("BOUND");
        submissionRepository.save(submission);
        if ("project.cover.generate".equals(command.actionKey())) {
            projectApi.markCoverExecutionStarted(command.projectId(), run.getId());
        }
        publishDispatch(run, command);
        return toApiView(run);
    }

    @Transactional
    public void dispatchDeferred(Long executionRunId, AigcActionCommand command) {
        var snapshot = runRepository.findById(executionRunId).orElse(null);
        if (snapshot == null) {
            return;
        }
        var project = lockDispatchProject(snapshot);
        if (project == null) {
            return;
        }
        var run = runRepository.findLockedById(executionRunId).orElse(null);
        if (run == null
                || !snapshot.getProjectId().equals(run.getProjectId())
                || !AigcExecutionRunStatus.PENDING.equals(run.getStatus())) {
            return;
        }
        AigcProjectObjectView object = null;
        if (run.getObjectId() != null) {
            object =
                    projectApi.getGraph(run.getProjectId()).objects().stream()
                            .filter(candidate -> run.getObjectId().equals(candidate.id()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    GlobalErrorCode.NOT_FOUND, "项目对象不存在"));
        }
        var binding = new AigcExecutionBinding();
        binding.setTargetType(run.getTargetType());
        binding.setTargetRef(run.getTargetRef());
        if ("package.generate".equals(run.getActionKey())) {
            run.setStatus(AigcExecutionRunStatus.RUNNING);
            run.setStartTime(LocalDateTime.now());
            run.setVersion(run.getVersion() + 1);
            runRepository.save(run);
            if (fanOutOrchestration(run, project, command) == 0) {
                run.setStatus(AigcExecutionRunStatus.SUCCEEDED);
                run.setEndTime(LocalDateTime.now());
                run.setVersion(run.getVersion() + 1);
                runRepository.save(run);
                terminalService.onRunTerminal(run);
            }
        } else {
            var executor = requireExecutor(run.getTargetType());
            executor.execute(new AigcActionContext(project, object, binding, run, command));
        }
        submissionRepository
                .findLockedById(run.getExecutionSubmissionId())
                .ifPresent(
                        submission -> {
                            if (!"TERMINAL".equals(submission.getStatus())) {
                                submission.setStatus("DISPATCHED");
                                submissionRepository.save(submission);
                            }
                        });
    }

    @Transactional
    public void failDeferredDispatch(Long executionRunId, String errorMessage) {
        var snapshot = runRepository.findById(executionRunId).orElse(null);
        if (snapshot == null || lockDispatchProject(snapshot) == null) {
            return;
        }
        var run = runRepository.findLockedById(executionRunId).orElse(null);
        if (run == null
                || !snapshot.getProjectId().equals(run.getProjectId())
                || !AigcExecutionRunStatus.PENDING.equals(run.getStatus())) {
            return;
        }
        run.setStatus(AigcExecutionRunStatus.FAILED);
        run.setErrorMessage(
                errorMessage == null || errorMessage.isBlank() ? "执行派发失败" : errorMessage);
        run.setEndTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        runRepository.save(run);
        releaseRootReservation(run);
    }

    private AigcProjectView lockDispatchProject(AigcExecutionRun run) {
        var project = projectApi.requireProject(run.getProjectId());
        if ("project.cover.generate".equals(run.getActionKey())) {
            projectApi.lockForCoverMutation(run.getProjectId(), project.userId());
        } else {
            projectApi.lockForGeneratedResource(run.getProjectId(), project.userId());
        }
        var currentProject = projectApi.requireProject(run.getProjectId());
        return com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle.ARCHIVED.equals(
                        currentProject.lifecycleStage())
                ? null
                : currentProject;
    }

    private int fanOutOrchestration(
            AigcExecutionRun root, AigcProjectView project, AigcActionCommand command) {
        var objectsById =
                projectApi.getGraph(project.id()).objects().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcProjectObjectView::id, item -> item));
        var submitted = 0;
        for (var objectId : root.getFrozenProjectObjectIds()) {
            var target = objectsById.get(objectId);
            if (target == null || target.defaultActionKey() == null) {
                continue;
            }
            submitChild(
                    new AigcChildActionCommand(
                            project.id(),
                            target.id(),
                            target.defaultActionKey(),
                            root.getId(),
                            root.getId(),
                            "fanout." + target.stableKey(),
                            command.prompt(),
                            command.attachmentMediaVersionIds(),
                            "%s:%s:%s"
                                    .formatted(
                                            root.getExecutionSubmissionId(),
                                            target.id(),
                                            target.defaultActionKey())));
            submitted++;
        }
        return submitted;
    }

    private void publishDispatch(AigcExecutionRun run, AigcActionCommand command) {
        eventPublisher.publishEvent(
                new com.xuejiai.aaf.module.ai.aigc.execution.event
                        .AigcExecutionRunDispatchRequestedEvent(run.getId(), command));
    }

    private AigcActionExecutor requireExecutor(String targetType) {
        return executors.stream()
                .filter(candidate -> candidate.supports(targetType))
                .findFirst()
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        GlobalErrorCode.BAD_REQUEST, "不支持的执行目标: " + targetType));
    }

    @Override
    @Transactional
    public AigcExecutionRunView submitChild(AigcChildActionCommand command) {
        var parentSnapshot = requireAccessibleRun(command.parentExecutionRunId());
        if (!Objects.equals(parentSnapshot.getProjectId(), command.projectId())
                || !Objects.equals(
                        parentSnapshot.getRootExecutionRunId(), command.rootExecutionRunId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "子 Run 与父级执行树不一致");
        }
        var project = requireWritableProject(command.projectId(), command.actionKey());
        var parent =
                runRepository
                        .findLockedById(command.parentExecutionRunId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "父执行 Run 不存在"));
        var root =
                runRepository
                        .findLockedById(command.rootExecutionRunId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行根 Run 不存在"));
        if (!Objects.equals(root.getId(), root.getRootExecutionRunId())
                || !"ORCHESTRATION".equals(root.getRunKind())
                || !root.getStatus().isActive()
                || !parent.getStatus().isActive()
                || root.getExecutionSubmissionId() == null
                || root.getExecutionReservationId() == null
                || !Objects.equals(parent.getProjectId(), root.getProjectId())
                || !Objects.equals(
                        parent.getExecutionSubmissionId(), root.getExecutionSubmissionId())
                || !Objects.equals(
                        parent.getExecutionReservationId(), root.getExecutionReservationId())
                || !Objects.equals(parent.getRootExecutionRunId(), root.getId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "子 Run 与 root 绑定证据不一致");
        }
        var submission =
                submissionRepository
                        .findLockedById(root.getExecutionSubmissionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行 submission 不存在"));
        if (!"BOUND".equals(submission.getStatus())
                || !Objects.equals(submission.getProjectId(), root.getProjectId())
                || !Objects.equals(submission.getReservationId(), root.getExecutionReservationId())
                || !Objects.equals(submission.getRootExecutionRunId(), root.getId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "执行 submission 未绑定到指定 root");
        }
        var reservation =
                projectApi.requireBoundExecution(
                        root.getExecutionReservationId(), submission.getId(), root.getId());
        if (!Objects.equals(reservation.projectId(), root.getProjectId())
                || !reservation.frozenProjectObjectIds().contains(command.projectObjectId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "子 Run 目标不在冻结执行范围内");
        }
        var existing = findFirstChild(command);
        if (existing.isPresent()) {
            return toApiView(requireSameChildRequest(existing.get(), command));
        }
        var object =
                projectApi.getGraph(project.id()).objects().stream()
                        .filter(candidate -> command.projectObjectId().equals(candidate.id()))
                        .findFirst()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "项目对象不存在"));
        var binding = bindingResolver.resolve(project, command.actionKey());
        var childCommand =
                new AigcActionCommand(
                        command.projectId(),
                        command.projectObjectId(),
                        command.actionKey(),
                        command.prompt(),
                        null,
                        Map.of(),
                        command.attachmentMediaVersionIds(),
                        List.of(command.projectObjectId()),
                        root.getTargetGraphRevision(),
                        true,
                        command.idempotencyKey());
        var run = new AigcExecutionRun();
        run.setOrgId(root.getOrgId());
        run.setWorkspaceId(root.getWorkspaceId());
        run.setOwnerId(root.getOwnerId());
        run.setProjectId(root.getProjectId());
        run.setObjectId(object.id());
        run.setParentExecutionRunId(parent.getId());
        run.setRootExecutionRunId(root.getId());
        run.setRunKind("ACTIVITY");
        run.setWorkflowNodeKey(command.workflowNodeKey());
        run.setExecutionSubmissionId(root.getExecutionSubmissionId());
        run.setExecutionReservationId(root.getExecutionReservationId());
        run.setTargetGraphRevision(root.getTargetGraphRevision());
        run.setFrozenProjectObjectIds(root.getFrozenProjectObjectIds());
        run.setBindingVersionId(binding.getId());
        run.setActionKey(command.actionKey());
        run.setTargetType(binding.getTargetType());
        run.setTargetRef(binding.getTargetRef());
        run.setStatus(AigcExecutionRunStatus.PENDING);
        run.setGenerationMode(project.generationMode());
        run.setPromptText(command.prompt());
        run.setAttachmentRefs(command.attachmentMediaVersionIds());
        run.setCostCredits(binding.getEstimatedCredits());
        run.setEffectiveInput(effectiveInput(project, object, childCommand));
        runRepository.saveAndFlush(run);
        publishDispatch(run, childCommand);
        return toApiView(run);
    }

    @Override
    @Transactional(readOnly = true)
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_EXECUTION_RUN_READ)
    public AigcExecutionRunTreeView requireRunTree(Long rootExecutionRunId) {
        var root = requireAccessibleRun(rootExecutionRunId);
        if (!Objects.equals(root.getId(), root.getRootExecutionRunId())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "指定 Run 不是执行树根");
        }
        var descendants =
                runRepository.findByRootExecutionRunIdOrderByIdAsc(root.getId()).stream()
                        .filter(run -> !run.getId().equals(root.getId()))
                        .map(this::toApiView)
                        .toList();
        return new AigcExecutionRunTreeView(toApiView(root), descendants);
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_EXECUTION_RUN_EXECUTE)
    public AigcExecutionRunView cancel(Long executionRunId, String reason) {
        var requested = requireAccessibleRun(executionRunId);
        if (!requested.getStatus().isActive()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "当前执行状态不可取消");
        }
        var cancellationReason = reason == null || reason.isBlank() ? "用户取消" : reason;
        var preBindRoot =
                requested.getStatus() == AigcExecutionRunStatus.PENDING_BIND
                        && Objects.equals(requested.getId(), requested.getRootExecutionRunId());
        var tree =
                runRepository.findByRootExecutionRunIdOrderByIdAsc(
                        requested.getRootExecutionRunId());
        var byId =
                tree.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        AigcExecutionRun::getId,
                                        java.util.function.Function.identity()));
        var targets =
                Objects.equals(requested.getId(), requested.getRootExecutionRunId())
                                && "ORCHESTRATION".equals(requested.getRunKind())
                        ? tree
                        : tree.stream()
                                .filter(
                                        candidate ->
                                                isInSubtree(candidate, requested.getId(), byId))
                                .toList();
        targets.stream()
                .sorted(java.util.Comparator.comparing(AigcExecutionRun::getId).reversed())
                .filter(candidate -> candidate.getStatus().isActive())
                .forEach(candidate -> cancelRun(candidate, cancellationReason));
        var terminal =
                runRepository
                        .findById(requested.getId())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "执行记录不存在"));
        if (preBindRoot) {
            projectApi.releaseExecution(
                    new AigcProjectExecutionReservationReleaseCommand(
                            terminal.getExecutionReservationId(),
                            terminal.getExecutionSubmissionId(),
                            null,
                            "PRE_BIND_CANCEL"));
            submissionRepository
                    .findLockedById(terminal.getExecutionSubmissionId())
                    .ifPresent(
                            submission -> {
                                submission.setStatus("TERMINAL");
                                submission.setLastError(cancellationReason);
                                submissionRepository.save(submission);
                            });
            return toApiView(terminal);
        }
        terminalService.onRunTerminal(terminal);
        return toApiView(terminal);
    }

    @Override
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize(
            com.xuejiai.aaf.module.ai.aigc.AigcAuthorities.HAS_EXECUTION_RUN_EXECUTE)
    public AigcExecutionRunView retry(Long executionRunId, String idempotencyKey) {
        var previous = requireAccessibleRun(executionRunId);
        if ("ORCHESTRATION".equals(previous.getRunKind())
                && Objects.equals(previous.getId(), previous.getRootExecutionRunId())
                && (previous.getStatus() == AigcExecutionRunStatus.PARTIALLY_SUCCEEDED
                        || previous.getStatus() == AigcExecutionRunStatus.RUNNING)) {
            return retryPartialRoot(previous, idempotencyKey);
        }
        if (!previous.getStatus().isRetryable()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "当前执行状态不可重试");
        }
        var command =
                new AigcActionCommand(
                        previous.getProjectId(),
                        previous.getObjectId(),
                        previous.getActionKey(),
                        previous.getPromptText(),
                        text(previous.getEffectiveInput(), "requestedModelId"),
                        objectMap(previous.getEffectiveInput(), "actionArguments"),
                        previous.getAttachmentRefs(),
                        previous.getFrozenProjectObjectIds(),
                        previous.getTargetGraphRevision(),
                        true,
                        idempotencyKey);
        var retryRoot =
                previous.getRetryOfExecutionRunId() == null
                        ? previous.getId()
                        : previous.getRetryOfExecutionRunId();
        var result = prepareSubmission(command);
        var retry = runRepository.findLockedById(result.id()).orElseThrow();
        retry.setRetryOfExecutionRunId(retryRoot);
        retry.setRetryCount(previous.getRetryCount() + 1);
        runRepository.save(retry);
        return toApiView(retry);
    }

    private AigcExecutionRunView retryPartialRoot(
            AigcExecutionRun snapshot, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "局部重试幂等键不能为空");
        }
        var recordedKey = text(snapshot.getOutputPayload(), "partialRetryIdempotencyKey");
        if (snapshot.getStatus() == AigcExecutionRunStatus.RUNNING) {
            if (Objects.equals(recordedKey, idempotencyKey)) {
                return toApiView(snapshot);
            }
            throw new BusinessException(409, "同 root 局部重试正在执行");
        }
        projectApi.bindExecution(
                new AigcProjectExecutionReservationBindCommand(
                        snapshot.getExecutionReservationId(),
                        snapshot.getExecutionSubmissionId(),
                        snapshot.getId()));
        var submission =
                submissionRepository
                        .findLockedById(snapshot.getExecutionSubmissionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行 submission 不存在"));
        submission.setStatus("BOUND");
        submission.setLastError(null);
        submissionRepository.save(submission);
        var root =
                runRepository
                        .findLockedById(snapshot.getId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "执行根 Run 不存在"));
        var currentRetryKey = text(root.getOutputPayload(), "partialRetryIdempotencyKey");
        if (root.getStatus() == AigcExecutionRunStatus.RUNNING) {
            if (Objects.equals(currentRetryKey, idempotencyKey)) {
                return toApiView(root);
            }
            throw new BusinessException(409, "同 root 局部重试正在执行");
        }
        if (root.getStatus() != AigcExecutionRunStatus.PARTIALLY_SUCCEEDED) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "当前 root 状态不可局部重试");
        }
        var latestByLineage = new LinkedHashMap<Long, AigcExecutionRun>();
        for (var child :
                runRepository.findByRootExecutionRunIdOrderByIdAsc(root.getId()).stream()
                        .filter(candidate -> !Objects.equals(candidate.getId(), root.getId()))
                        .toList()) {
            var lineageId =
                    child.getRetryOfExecutionRunId() == null
                            ? child.getId()
                            : child.getRetryOfExecutionRunId();
            var current = latestByLineage.get(lineageId);
            if (current == null || child.getRetryCount() > current.getRetryCount()) {
                latestByLineage.put(lineageId, child);
            }
        }
        var retryable =
                latestByLineage.values().stream()
                        .filter(child -> child.getStatus().isRetryable())
                        .toList();
        if (retryable.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "当前执行树没有可局部重试节点");
        }
        var output = new LinkedHashMap<String, Object>();
        if (root.getOutputPayload() != null) {
            output.putAll(root.getOutputPayload());
        }
        output.put("partialRetryIdempotencyKey", idempotencyKey);
        root.setOutputPayload(output);
        root.setStatus(AigcExecutionRunStatus.RUNNING);
        root.setEndTime(null);
        root.setErrorMessage(null);
        root.setVersion(root.getVersion() + 1);
        runRepository.save(root);
        for (var failed : retryable) {
            createLocalRetry(root, failed, idempotencyKey);
        }
        return toApiView(root);
    }

    private void createLocalRetry(
            AigcExecutionRun root, AigcExecutionRun previous, String idempotencyKey) {
        var retry = new AigcExecutionRun();
        retry.setOrgId(root.getOrgId());
        retry.setWorkspaceId(root.getWorkspaceId());
        retry.setOwnerId(root.getOwnerId());
        retry.setProjectId(root.getProjectId());
        retry.setObjectId(previous.getObjectId());
        retry.setParentExecutionRunId(previous.getParentExecutionRunId());
        retry.setRootExecutionRunId(root.getId());
        retry.setRunKind(previous.getRunKind());
        retry.setWorkflowNodeKey(previous.getWorkflowNodeKey());
        retry.setExecutionSubmissionId(root.getExecutionSubmissionId());
        retry.setExecutionReservationId(root.getExecutionReservationId());
        retry.setTargetGraphRevision(root.getTargetGraphRevision());
        retry.setFrozenProjectObjectIds(root.getFrozenProjectObjectIds());
        retry.setBindingVersionId(previous.getBindingVersionId());
        retry.setActionKey(previous.getActionKey());
        retry.setTargetType(previous.getTargetType());
        retry.setTargetRef(previous.getTargetRef());
        retry.setStatus(AigcExecutionRunStatus.PENDING);
        retry.setGenerationMode(previous.getGenerationMode());
        retry.setSelectedModelVersion(previous.getSelectedModelVersion());
        retry.setPromptText(previous.getPromptText());
        retry.setAttachmentRefs(previous.getAttachmentRefs());
        retry.setCostCredits(previous.getCostCredits());
        retry.setEffectiveInput(previous.getEffectiveInput());
        retry.setRetryOfExecutionRunId(
                previous.getRetryOfExecutionRunId() == null
                        ? previous.getId()
                        : previous.getRetryOfExecutionRunId());
        retry.setRetryCount(previous.getRetryCount() + 1);
        runRepository.saveAndFlush(retry);
        var command =
                new AigcActionCommand(
                        retry.getProjectId(),
                        retry.getObjectId(),
                        retry.getActionKey(),
                        retry.getPromptText(),
                        text(retry.getEffectiveInput(), "requestedModelId"),
                        objectMap(retry.getEffectiveInput(), "actionArguments"),
                        retry.getAttachmentRefs(),
                        List.of(retry.getObjectId()),
                        retry.getTargetGraphRevision(),
                        true,
                        "%s:%d".formatted(idempotencyKey, previous.getId()));
        publishDispatch(retry, command);
    }

    @Override
    @Transactional(readOnly = true)
    public AigcExecutionRunView requireRun(Long executionRunId) {
        return toApiView(requireAccessibleRun(executionRunId));
    }

    @Override
    @Transactional
    public void cancelProjectCoverRuns(Long projectId, String reason) {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        projectApi.lockForCoverMutation(projectId, userId);
        supersedeCoverRuns(projectId, reason == null || reason.isBlank() ? "封面已手动变更" : reason);
    }

    private void supersedeCoverRuns(Long projectId, String reason) {
        runRepository
                .findByProjectIdAndActionKeyAndDeletedFalseOrderByIdDesc(
                        projectId, "project.cover.generate")
                .forEach(
                        run -> {
                            if (run.getStatus().isActive()) {
                                cancel(run.getId(), reason);
                            }
                            var output = new LinkedHashMap<String, Object>();
                            if (run.getOutputPayload() != null) {
                                output.putAll(run.getOutputPayload());
                            }
                            output.put("coverSuperseded", true);
                            run.setOutputPayload(output);
                            runRepository.save(run);
                        });
    }

    private AigcProjectView requireWritableProject(Long projectId, String actionKey) {
        var userId = operatorContext.currentOwnerId().orElseThrow();
        if ("project.cover.generate".equals(actionKey)) {
            projectApi.lockForCoverMutation(projectId, userId);
        } else {
            projectApi.lockForGeneratedResource(projectId, userId);
        }
        var project = projectApi.requireProject(projectId);
        if (!project.lifecycleStage().allowsCreativeMutation()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "当前项目阶段不可执行新动作: " + project.lifecycleStage());
        }
        return project;
    }

    private AigcProjectObjectView resolveObject(
            AigcProjectView project, AigcActionCommand command) {
        if ("project.cover.generate".equals(command.actionKey())) {
            return null;
        }
        var objects = projectApi.getGraph(project.id()).objects();
        var applicableTypes = applicableObjectTypes(command.actionKey());
        if (command.projectObjectId() != null) {
            var object =
                    objects.stream()
                            .filter(candidate -> command.projectObjectId().equals(candidate.id()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new BusinessException(
                                                    GlobalErrorCode.NOT_FOUND, "项目对象不存在"));
            if (!applicableTypes.contains(object.objectType())) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST,
                        "动作 %s 不适用于对象类型 %s".formatted(command.actionKey(), object.objectType()));
            }
            return object;
        }
        return objects.stream()
                .filter(object -> applicableTypes.contains(object.objectType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "未找到动作目标对象"));
    }

    private AigcExecutionRun createRootRun(
            AigcProjectView project,
            AigcProjectObjectView object,
            AigcExecutionBinding binding,
            AigcActionCommand command,
            Long submissionId,
            com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationView
                    reservation) {
        var run = new AigcExecutionRun();
        run.setOrgId(OrgContext.getCurrentOrgId());
        run.setWorkspaceId(OrgContext.getCurrentWorkspaceId());
        run.setOwnerId(operatorContext.currentOwnerId().orElseThrow());
        run.setProjectId(project.id());
        run.setObjectId(object == null ? null : object.id());
        run.setRunKind(
                "workflow".equalsIgnoreCase(binding.getTargetType())
                                || "package.generate".equals(command.actionKey())
                        ? "ORCHESTRATION"
                        : "ACTIVITY");
        run.setExecutionSubmissionId(submissionId);
        run.setExecutionReservationId(reservation.id());
        run.setTargetGraphRevision(reservation.targetGraphRevision());
        run.setFrozenProjectObjectIds(reservation.frozenProjectObjectIds());
        run.setBindingVersionId(binding.getId());
        run.setActionKey(command.actionKey());
        run.setTargetType(binding.getTargetType());
        run.setTargetRef(binding.getTargetRef());
        run.setStatus(AigcExecutionRunStatus.PENDING_BIND);
        run.setGenerationMode(project.generationMode());
        run.setSelectedModelVersion(command.requestedModelId());
        run.setPromptText(command.prompt());
        run.setAttachmentRefs(command.attachmentMediaVersionIds());
        run.setCostCredits(binding.getEstimatedCredits());
        run.setRetryCount(0);
        run.setEffectiveInput(effectiveInput(project, object, command));
        runRepository.save(run);
        run.setRootExecutionRunId(run.getId());
        return runRepository.save(run);
    }

    private Map<String, Object> effectiveInput(
            AigcProjectView project, AigcProjectObjectView object, AigcActionCommand command) {
        var input = new LinkedHashMap<String, Object>();
        input.put("actionKey", command.actionKey());
        if (object != null) {
            input.put("objectId", object.id());
        }
        if (command.prompt() != null) {
            input.put("prompt", command.prompt());
        }
        if (command.requestedModelId() != null) {
            input.put("requestedModelId", command.requestedModelId());
        }
        input.put("actionArguments", command.actionArguments());
        if ("project.cover.generate".equals(command.actionKey())
                && project.coverMediaVersionId() != null) {
            input.put("expectedCoverMediaVersionId", project.coverMediaVersionId());
        }
        input.put("attachmentMediaVersionIds", command.attachmentMediaVersionIds());
        input.put("selectedProjectObjectIds", command.selectedProjectObjectIds());
        if (command.expectedGraphRevision() != null) {
            input.put("expectedGraphRevision", command.expectedGraphRevision());
        }
        input.put("idempotencyKey", command.idempotencyKey());
        return Map.copyOf(input);
    }

    private void validateBudget(AigcProjectView project, AigcExecutionBinding binding) {
        if (project.budgetLimit() == null || binding.getEstimatedCredits() == null) {
            return;
        }
        var used = project.costUsed() == null ? BigDecimal.ZERO : project.costUsed();
        if (used.add(binding.getEstimatedCredits()).compareTo(project.budgetLimit()) > 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "项目预算不足");
        }
    }

    private Optional<AigcActionOptionVO> toOption(AigcProjectView project, String actionKey) {
        return bindingResolver
                .resolveOptional(project, actionKey)
                .map(
                        binding ->
                                new AigcActionOptionVO(
                                        actionKey,
                                        ACTION_LABELS.getOrDefault(actionKey, actionKey),
                                        binding.getTargetType(),
                                        binding.getConfirmationRequired(),
                                        binding.getEstimatedCredits(),
                                        applicableObjectTypes(actionKey)));
    }

    private List<String> applicableObjectTypes(String actionKey) {
        return switch (actionKey) {
            case "brief.refine" -> List.of("brief");
            case "concept.generate" -> List.of("creative_concept");
            case "image.generate", "image.edit" -> List.of("image_deliverable", "shot_keyframe");
            case "video.generate" -> List.of("video_deliverable");
            case "package.generate" -> List.of("deliverable_set");
            case "outline.generate" -> List.of("article_outline");
            case "article.draft", "article.rewrite", "article.seo_optimize" ->
                    List.of("article_deliverable");
            default -> List.of("copy_deliverable");
        };
    }

    private boolean isInSubtree(
            AigcExecutionRun candidate, Long subtreeRootId, Map<Long, AigcExecutionRun> byId) {
        var current = candidate;
        while (current != null) {
            if (Objects.equals(current.getId(), subtreeRootId)) {
                return true;
            }
            current = byId.get(current.getParentExecutionRunId());
        }
        return false;
    }

    private void cancelRun(AigcExecutionRun run, String reason) {
        var runtimeTraceId = text(run.getOutputPayload(), "runtimeTraceId");
        if (runtimeTraceId != null) {
            runtimeCancellationPort.cancel(run.getTargetType(), runtimeTraceId, reason);
        }
        taskRefRepository
                .findByExecutionRunIdAndDeletedFalseOrderBySortOrderAsc(run.getId())
                .forEach(reference -> taskApi.cancel(reference.getTaskId(), reason));
        run.setStatus(AigcExecutionRunStatus.CANCELED);
        run.setErrorMessage(reason);
        run.setEndTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        runRepository.save(run);
    }

    private Optional<AigcExecutionRun> findFirstChild(AigcChildActionCommand command) {
        return runRepository
                .findFirstByParentExecutionRunIdAndWorkflowNodeKeyAndObjectIdAndRetryOfExecutionRunIdIsNull(
                        command.parentExecutionRunId(),
                        command.workflowNodeKey(),
                        command.projectObjectId());
    }

    private AigcExecutionRun requireSameChildRequest(
            AigcExecutionRun existing, AigcChildActionCommand command) {
        var sameRequest =
                Objects.equals(existing.getProjectId(), command.projectId())
                        && Objects.equals(
                                existing.getParentExecutionRunId(), command.parentExecutionRunId())
                        && Objects.equals(
                                existing.getRootExecutionRunId(), command.rootExecutionRunId())
                        && Objects.equals(existing.getObjectId(), command.projectObjectId())
                        && Objects.equals(existing.getWorkflowNodeKey(), command.workflowNodeKey())
                        && Objects.equals(existing.getActionKey(), command.actionKey())
                        && Objects.equals(existing.getPromptText(), command.prompt())
                        && Objects.equals(
                                existing.getAttachmentRefs(), command.attachmentMediaVersionIds());
        if (!sameRequest) {
            throw new BusinessException(409, "子 Run 幂等键参数冲突");
        }
        return existing;
    }

    private AigcExecutionRun requireAccessibleRun(Long executionRunId) {
        var run =
                runRepository
                        .findById(executionRunId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "执行记录不存在"));
        projectApi.requireProject(run.getProjectId());
        return run;
    }

    private AigcExecutionRunView toApiView(AigcExecutionRun run) {
        var taskIds =
                taskRefRepository
                        .findByExecutionRunIdAndDeletedFalseOrderBySortOrderAsc(run.getId())
                        .stream()
                        .map(reference -> reference.getTaskId())
                        .toList();
        var objectVersionIds = longList(run.getOutputPayload(), "objectVersionIds");
        var mediaVersionIds = longList(run.getOutputPayload(), "mediaVersionIds");
        return new AigcExecutionRunView(
                run.getId(),
                run.getProjectId(),
                run.getObjectId(),
                run.getParentExecutionRunId(),
                run.getRootExecutionRunId(),
                run.getRunKind(),
                run.getWorkflowNodeKey(),
                run.getExecutionSubmissionId(),
                run.getExecutionReservationId(),
                run.getTargetGraphRevision(),
                run.getFrozenProjectObjectIds(),
                run.getEffectiveInput(),
                run.getBindingVersionId(),
                run.getActionKey(),
                run.getTargetType(),
                run.getTargetRef(),
                run.getStatus(),
                taskIds,
                objectVersionIds,
                mediaVersionIds,
                text(run.getOutputPayload(), "runtimeTraceId"),
                text(run.getOutputPayload(), "runtimeRunId"),
                text(run.getOutputPayload(), "output"),
                run.getCostCredits() == null ? null : run.getCostCredits().longValue());
    }

    private void releaseRootReservation(AigcExecutionRun run) {
        terminalService.onRunTerminal(run);
    }

    private Object value(Map<String, Object> payload, String field) {
        return payload == null ? null : payload.get(field);
    }

    private String text(Map<String, Object> payload, String field) {
        var value = value(payload, field);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private Map<String, Object> objectMap(Map<String, Object> payload, String field) {
        if (payload == null || !(payload.get(field) instanceof Map<?, ?> values)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        values.forEach(
                (key, mapValue) -> {
                    if (key instanceof String name) {
                        result.put(name, mapValue);
                    }
                });
        return result;
    }

    private List<Long> longList(Map<String, Object> payload, String field) {
        if (payload == null || !(payload.get(field) instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .toList();
    }
}
