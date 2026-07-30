package com.xuejiai.aaf.module.content.service.action;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_ACTION_NOT_ALLOWED;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_BUDGET_EXCEEDED;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_CONFIRMATION_REQUIRED;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_EXECUTION_NOT_CANCELABLE;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_EXECUTION_NOT_RETRYABLE;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_EXECUTION_TARGET_INVALID;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_OBJECT_NOT_FOUND;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.content.ContentConfigStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentExecutionStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentObjectTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.content.domain.ContentExecutionBinding;
import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.domain.ContentProjectBlueprint;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;
import com.xuejiai.aaf.module.content.repository.ContentExecutionRunRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectBlueprintRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectObjectRepository;
import com.xuejiai.aaf.module.content.service.ContentExecutionRunService;
import com.xuejiai.aaf.module.content.service.ContentProjectAccessGuard;
import com.xuejiai.aaf.module.content.vo.ContentActionCommandDTO;
import com.xuejiai.aaf.module.content.vo.ContentActionOptionVO;
import com.xuejiai.aaf.module.content.vo.ContentExecutionRunVO;

import lombok.RequiredArgsConstructor;

/**
 * Content Studio 动作命令服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ContentActionCommandService {

    private static final Map<String, String> ACTION_LABELS =
            Map.of(
                    "brief.refine", "完善简报",
                    "concept.generate", "生成创意方向",
                    "copy.generate", "生成文案",
                    "image.generate", "生成图片",
                    "image.edit", "局部修改图片",
                    "deliverable.regenerate", "重新生成交付物");

    private final ContentProjectAccessGuard accessGuard;
    private final ContentProjectObjectRepository objectRepository;
    private final ContentProjectBlueprintRepository blueprintRepository;
    private final ContentExecutionRunRepository runRepository;
    private final ContentExecutionRunService runService;
    private final ContentExecutionBindingResolver bindingResolver;
    private final List<ContentActionExecutor> executors;

    @Transactional(readOnly = true)
    public List<ContentActionOptionVO> listActions(Long projectId) {
        var project = accessGuard.requireWritableProject(projectId);
        var actionKeys = declaredActionKeys(project);
        return actionKeys.stream()
                .map(actionKey -> toOption(project, actionKey))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ContentExecutionRunVO execute(Long projectId, ContentActionCommandDTO command) {
        var project = accessGuard.requireWritableProject(projectId);
        validateActionAllowed(project, command.actionKey());
        var binding = bindingResolver.resolve(project, command.actionKey());
        validateBudget(project, binding);
        validateConfirmation(binding, command);
        var object = resolveObject(project, command);
        var run = createRun(project, object, binding, command);
        var executor =
                executors.stream()
                        .filter(candidate -> candidate.supports(binding.getTargetType()))
                        .findFirst()
                        .orElseThrow(() -> exception(CONTENT_EXECUTION_TARGET_INVALID));
        try {
            executor.execute(new ContentActionContext(project, object, binding, run, command));
        } catch (BusinessException businessException) {
            markFailed(run, businessException.getMessage());
            throw businessException;
        }
        return runService.toView(runRepository.findById(run.getId()).orElse(run));
    }

    @Transactional
    public ContentExecutionRunVO cancel(Long runId) {
        var run = requireRun(runId);
        accessGuard.requireWritableProject(run.getProjectId());
        if (!ContentExecutionStatusEnum.PENDING.getCode().equals(run.getStatus())
                && !ContentExecutionStatusEnum.RUNNING.getCode().equals(run.getStatus())) {
            throw exception(CONTENT_EXECUTION_NOT_CANCELABLE);
        }
        run.setStatus(ContentExecutionStatusEnum.CANCELED.getCode());
        run.setEndTime(LocalDateTime.now());
        return runService.toView(runRepository.save(run));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ContentExecutionRunVO retry(Long runId) {
        var run = requireRun(runId);
        if (!ContentExecutionStatusEnum.FAILED.getCode().equals(run.getStatus())
                && !ContentExecutionStatusEnum.CANCELED.getCode().equals(run.getStatus())) {
            throw exception(CONTENT_EXECUTION_NOT_RETRYABLE);
        }
        var snippetIds = run.getSnippetRefs().stream().map(Long::valueOf).toList();
        var command =
                new ContentActionCommandDTO(
                        run.getActionKey(),
                        run.getObjectId(),
                        run.getPromptText(),
                        snippetIds,
                        run.getAttachmentRefs(),
                        run.getGenerationMode(),
                        true);
        return execute(run.getProjectId(), command);
    }

    private void validateActionAllowed(ContentProject project, String actionKey) {
        var actionKeys = declaredActionKeys(project);
        if (actionKeys.isEmpty() || !actionKeys.contains(actionKey)) {
            throw exception(CONTENT_ACTION_NOT_ALLOWED, actionKey);
        }
    }

    private List<String> declaredActionKeys(ContentProject project) {
        return resolveBlueprint(project)
                .map(ContentProjectBlueprint::getActionKeys)
                .filter(keys -> !keys.isEmpty())
                .orElse(List.of());
    }

    private Optional<ContentProjectBlueprint> resolveBlueprint(ContentProject project) {
        if (project.getBlueprintCode() == null
                || project.getBlueprintCode().isBlank()
                || project.getBlueprintVersion() == null
                || project.getBlueprintVersion().isBlank()) {
            return Optional.empty();
        }
        return blueprintRepository.findFirstByCodeAndBlueprintVersionAndStatusOrderByIdDesc(
                project.getBlueprintCode(),
                project.getBlueprintVersion(),
                ContentConfigStatusEnum.PUBLISHED.getCode());
    }

    private void validateBudget(ContentProject project, ContentExecutionBinding binding) {
        if (project.getBudgetLimit() == null || binding.getEstimatedCredits() == null) {
            return;
        }
        var used = project.getCostUsed() == null ? BigDecimal.ZERO : project.getCostUsed();
        if (used.add(binding.getEstimatedCredits()).compareTo(project.getBudgetLimit()) > 0) {
            throw exception(CONTENT_BUDGET_EXCEEDED);
        }
    }

    private void validateConfirmation(
            ContentExecutionBinding binding, ContentActionCommandDTO command) {
        if (Boolean.TRUE.equals(binding.getConfirmationRequired())
                && !Boolean.TRUE.equals(command.confirmed())) {
            throw exception(CONTENT_CONFIRMATION_REQUIRED);
        }
    }

    private ContentProjectObject resolveObject(
            ContentProject project, ContentActionCommandDTO command) {
        if (command.objectId() != null) {
            return objectRepository
                    .findById(command.objectId())
                    .filter(object -> project.getId().equals(object.getProjectId()))
                    .orElseThrow(() -> exception(CONTENT_OBJECT_NOT_FOUND));
        }
        var applicableTypes = applicableObjectTypes(command.actionKey());
        return objectRepository.findByProjectIdOrderBySortOrderAscIdAsc(project.getId()).stream()
                .filter(object -> applicableTypes.contains(object.getObjectType()))
                .findFirst()
                .orElseThrow(() -> exception(CONTENT_OBJECT_NOT_FOUND));
    }

    private ContentExecutionRun createRun(
            ContentProject project,
            ContentProjectObject object,
            ContentExecutionBinding binding,
            ContentActionCommandDTO command) {
        var run = new ContentExecutionRun();
        run.setOrgId(project.getOrgId());
        run.setWorkspaceId(project.getWorkspaceId());
        run.setOwnerId(project.getOwnerId());
        run.setProjectId(project.getId());
        run.setObjectId(object.getId());
        run.setActionKey(command.actionKey());
        run.setTargetType(binding.getTargetType());
        run.setTargetRef(binding.getTargetRef());
        run.setStatus(ContentExecutionStatusEnum.PENDING.getCode());
        run.setGenerationMode(
                command.generationMode() == null
                        ? project.getGenerationMode()
                        : command.generationMode());
        run.setPromptText(command.prompt());
        run.setSnippetRefs(
                command.snippetIds() == null
                        ? List.of()
                        : command.snippetIds().stream().map(String::valueOf).toList());
        run.setAttachmentRefs(
                command.attachmentRefs() == null
                        ? List.of()
                        : List.copyOf(command.attachmentRefs()));
        run.setCostCredits(binding.getEstimatedCredits());
        var input = new LinkedHashMap<String, Object>();
        input.put("actionKey", command.actionKey());
        input.put("objectId", object.getId());
        input.put("prompt", command.prompt());
        input.put("snippetIds", command.snippetIds());
        input.put("attachmentRefs", command.attachmentRefs());
        input.put("generationMode", run.getGenerationMode());
        run.setInputPayload(input);
        return runRepository.save(run);
    }

    private Optional<ContentActionOptionVO> toOption(ContentProject project, String actionKey) {
        return bindingResolver
                .resolveOptional(project, actionKey)
                .map(
                        binding ->
                                new ContentActionOptionVO(
                                        actionKey,
                                        ACTION_LABELS.getOrDefault(actionKey, actionKey),
                                        binding.getTargetType(),
                                        binding.getConfirmationRequired(),
                                        binding.getEstimatedCredits(),
                                        applicableObjectTypes(actionKey)));
    }

    private List<String> applicableObjectTypes(String actionKey) {
        return switch (actionKey) {
            case "brief.refine" -> List.of(ContentObjectTypeEnum.BRIEF.getCode());
            case "concept.generate" -> List.of(ContentObjectTypeEnum.CREATIVE_CONCEPT.getCode());
            case "image.generate", "image.edit" ->
                    List.of(
                            ContentObjectTypeEnum.IMAGE_DELIVERABLE.getCode(),
                            ContentObjectTypeEnum.SHOT_KEYFRAME.getCode());
            default -> List.of(ContentObjectTypeEnum.COPY_DELIVERABLE.getCode());
        };
    }

    private ContentExecutionRun requireRun(Long runId) {
        return accessGuard.requireRun(runId);
    }

    private void markFailed(ContentExecutionRun run, String message) {
        run.setStatus(ContentExecutionStatusEnum.FAILED.getCode());
        run.setErrorMessage(message);
        run.setEndTime(LocalDateTime.now());
        runRepository.save(run);
    }
}
