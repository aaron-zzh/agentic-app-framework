package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionApi;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunView;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeCancellationPort;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionTaskRefRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcActionOptionVO;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
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
                    Map.entry("deliverable.regenerate", "重新生成交付物"),
                    Map.entry("outline.generate", "生成文章提纲"),
                    Map.entry("article.draft", "撰写文章草稿"),
                    Map.entry("article.rewrite", "改写文章"),
                    Map.entry("article.seo_optimize", "优化文章 SEO"));

    private final AigcProjectApi projectApi;
    private final AigcExecutionBindingResolver bindingResolver;
    private final AigcExecutionRunRepository runRepository;
    private final AigcExecutionTaskRefRepository taskRefRepository;
    private final AigcTaskApi taskApi;
    private final AigcRuntimeCancellationPort runtimeCancellationPort;
    private final OperatorContext operatorContext;
    private final List<AigcActionExecutor> executors;

    @Transactional(readOnly = true)
    public List<AigcActionOptionVO> listActions(Long projectId) {
        var project = requireWritableProject(projectId);
        return ACTION_LABELS.keySet().stream()
                .map(actionKey -> toOption(project, actionKey))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AigcExecutionRunView submit(AigcActionCommand command) {
        return submitInternal(command, null, 0);
    }

    @Override
    @Transactional
    public AigcExecutionRunView cancel(Long executionRunId, String reason) {
        var run = requireAccessibleRun(executionRunId);
        if (!"pending".equals(run.getStatus()) && !"running".equals(run.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "当前执行状态不可取消");
        }
        var cancellationReason = reason == null || reason.isBlank() ? "用户取消" : reason;
        var runtimeTraceId = text(run.getOutputPayload(), "runtimeTraceId");
        if (runtimeTraceId != null) {
            runtimeCancellationPort.cancel(
                    run.getTargetType(), runtimeTraceId, cancellationReason);
        }
        taskRefRepository
                .findByExecutionRunIdAndDeletedFalseOrderBySortOrderAsc(run.getId())
                .forEach(
                        reference ->
                                taskApi.cancel(reference.getTaskId(), cancellationReason));
        run.setStatus("canceled");
        run.setErrorMessage(cancellationReason);
        run.setEndTime(LocalDateTime.now());
        run.setVersion(run.getVersion() + 1);
        runRepository.save(run);
        return toApiView(run);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AigcExecutionRunView retry(Long executionRunId, String idempotencyKey) {
        var previous = requireAccessibleRun(executionRunId);
        if (!"failed".equals(previous.getStatus()) && !"canceled".equals(previous.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "当前执行状态不可重试");
        }
        var command =
                new AigcActionCommand(
                        previous.getProjectId(),
                        previous.getObjectId(),
                        previous.getActionKey(),
                        previous.getPromptText(),
                        previous.getAttachmentRefs(),
                        true,
                        idempotencyKey);
        var retryRoot =
                previous.getRetryOfRunId() == null ? previous.getId() : previous.getRetryOfRunId();
        return submitInternal(command, retryRoot, previous.getRetryCount() + 1);
    }

    @Override
    @Transactional(readOnly = true)
    public AigcExecutionRunView requireRun(Long executionRunId) {
        return toApiView(requireAccessibleRun(executionRunId));
    }

    private AigcExecutionRunView submitInternal(
            AigcActionCommand command, Long retryOfRunId, int retryCount) {
        var project = requireWritableProject(command.projectId());
        var object = resolveObject(project, command);
        var binding = bindingResolver.resolve(project, command.actionKey());
        validateBudget(project, binding);
        if (Boolean.TRUE.equals(binding.getConfirmationRequired()) && !command.confirmed()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "动作需要确认后执行");
        }
        var run = createRun(project, object, binding, command, retryOfRunId, retryCount);
        var executor =
                executors.stream()
                        .filter(candidate -> candidate.supports(binding.getTargetType()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.BAD_REQUEST,
                                                "不支持的执行目标: " + binding.getTargetType()));
        try {
            executor.execute(new AigcActionContext(project, object, binding, run, command));
        } catch (RuntimeException exception) {
            run.setStatus("failed");
            run.setErrorMessage(exception.getMessage());
            run.setEndTime(LocalDateTime.now());
            run.setVersion(run.getVersion() + 1);
            runRepository.save(run);
            throw exception;
        }
        return toApiView(runRepository.findById(run.getId()).orElse(run));
    }

    private AigcProjectView requireWritableProject(Long projectId) {
        var project = projectApi.requireProject(projectId);
        if (!"draft".equals(project.lifecycleStage())
                && !"in_progress".equals(project.lifecycleStage())) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "当前项目阶段不可执行新动作: " + project.lifecycleStage());
        }
        return project;
    }

    private AigcProjectObjectView resolveObject(
            AigcProjectView project, AigcActionCommand command) {
        var objects = projectApi.getGraph(project.id()).objects();
        if (command.projectObjectId() != null) {
            return objects.stream()
                    .filter(object -> command.projectObjectId().equals(object.id()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "项目对象不存在"));
        }
        var applicableTypes = applicableObjectTypes(command.actionKey());
        return objects.stream()
                .filter(object -> applicableTypes.contains(object.objectType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "未找到动作目标对象"));
    }

    private AigcExecutionRun createRun(
            AigcProjectView project,
            AigcProjectObjectView object,
            AigcExecutionBinding binding,
            AigcActionCommand command,
            Long retryOfRunId,
            int retryCount) {
        var run = new AigcExecutionRun();
        run.setOrgId(OrgContext.getCurrentOrgId());
        run.setWorkspaceId(OrgContext.getCurrentWorkspaceId());
        run.setOwnerId(operatorContext.currentOwnerId().orElseThrow());
        run.setProjectId(project.id());
        run.setObjectId(object.id());
        run.setActionKey(command.actionKey());
        run.setTargetType(binding.getTargetType());
        run.setTargetRef(binding.getTargetRef());
        run.setStatus("pending");
        run.setGenerationMode(project.generationMode());
        run.setPromptText(command.prompt());
        run.setAttachmentRefs(command.attachmentMediaVersionIds());
        run.setCostCredits(binding.getEstimatedCredits());
        run.setRetryOfRunId(retryOfRunId);
        run.setRetryCount(retryCount);
        var input = new LinkedHashMap<String, Object>();
        input.put("actionKey", command.actionKey());
        input.put("objectId", object.id());
        if (command.prompt() != null) {
            input.put("prompt", command.prompt());
        }
        input.put("attachmentMediaVersionIds", command.attachmentMediaVersionIds());
        input.put("idempotencyKey", command.idempotencyKey());
        run.setInputPayload(input);
        return runRepository.save(run);
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
            case "outline.generate" -> List.of("article_outline");
            case "article.draft", "article.rewrite", "article.seo_optimize" ->
                    List.of("article_deliverable");
            default -> List.of("copy_deliverable");
        };
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

    private String text(Map<String, Object> payload, String field) {
        if (payload == null || payload.get(field) == null) {
            return null;
        }
        return String.valueOf(payload.get(field));
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
