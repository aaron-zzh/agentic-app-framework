package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionCategoryEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.AigcCanonicalRequest;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcBoundTaskEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskApi;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskSubmitCommand;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskView;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;
import com.xuejiai.aaf.module.ai.aigc.task.vo.VideoTaskRequest;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 现有媒体任务实现的跨子模块 API 适配器。 */
@Service
@RequiredArgsConstructor
public class AigcTaskApiAdapter implements AigcTaskApi {

    private final AigcTaskService taskService;
    private final AigcTaskRepository taskRepository;
    private final OperatorContext operatorContext;
    private final AigcBoundTaskEvidencePort boundTaskEvidencePort;
    private final AigcTaskIntentStore intentStore;
    private final AigcTaskProviderCapabilities providerCapabilities;
    private final CapabilityRouter capabilityRouter;
    private final AiCreditGuard creditGuard;
    private final AigcTaskExecutor taskExecutor;
    private final AigcActivityEventService activityEventService;

    @Override
    @Transactional
    public AigcTaskView submit(AigcTaskSubmitCommand command) {
        if (command == null || command.taskType() == null || command.taskType().isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "AIGC 子任务类型不能为空");
        }
        if (command.executionRunId() == null) {
            if (command.projectId() != null || command.projectObjectId() != null) {
                throw new BusinessException(
                        GlobalErrorCode.BAD_REQUEST, "独立 Task 不允许绑定 project");
            }
            return toView(requireTaskEntity(submitIndependent(command)));
        }
        if (command.projectId() == null
                || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "项目 Task 的 projectId、executionRunId 与 idempotencyKey 不能为空");
        }
        var evidence =
                boundTaskEvidencePort.requireBound(
                        command.executionRunId(), command.projectId(), command.projectObjectId());
        var currentOwner =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.UNAUTHORIZED, "当前用户未登录"));
        if (!Objects.equals(currentOwner, evidence.ownerId())) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "执行 Run 不存在");
        }

        var taskType = command.taskType().toUpperCase();
        var parameters = new LinkedHashMap<>(parseParameters(command.parametersJson()));
        var routingContext =
                CapabilityRoutingContext.of(
                        evidence.ownerId(), capability(taskType), command.modelId());
        var model = capabilityRouter.resolve(routingContext);
        var providerType = model.effectiveProviderType();
        var provider = providerType.name().toLowerCase();
        var capabilities = providerCapabilities.require(providerType);
        var providerKey =
                "task-"
                        + AigcCanonicalRequest.of(
                                        "task.provider",
                                        Map.of(
                                                "executionRunId", command.executionRunId(),
                                                "idempotencyKey", command.idempotencyKey()),
                                        Map.of())
                                .sha256()
                                .substring(0, 48);
        parameters.put("providerIdempotencyKey", providerKey);
        parameters.put("providerIdempotentSubmission", capabilities.idempotentSubmission());
        parameters.put("providerReceiptLookup", capabilities.receiptLookup());
        var requestHash =
                AigcCanonicalRequest.of(
                                "task.submit",
                                Map.of(
                                        "taskType", taskType,
                                        "modelId", model.getModelId(),
                                        "prompt", command.prompt() == null ? "" : command.prompt(),
                                        "parameters", parameters),
                                Map.of(
                                        "projectId", command.projectId(),
                                        "projectObjectId",
                                                command.projectObjectId() == null
                                                        ? 0L
                                                        : command.projectObjectId(),
                                        "executionRunId", command.executionRunId(),
                                        "providerKey", providerKey))
                        .sha256();
        var estimatedCredits =
                taskService.estimateCredits(
                        evidence.ownerId(), taskType, model.getModelId(), parameters);
        creditGuard.precheck(
                evidence.ownerId(), creditCategory(taskType), estimatedCredits);

        var intent = new AigcTask();
        intent.setUserId(evidence.ownerId());
        intent.setOwnerId(evidence.ownerId());
        intent.setOrgId(evidence.orgId());
        intent.setWorkspaceId(evidence.workspaceId());
        intent.setProjectId(command.projectId());
        intent.setProjectObjectId(command.projectObjectId());
        intent.setExecutionRunId(command.executionRunId());
        intent.setIdempotencyKey(command.idempotencyKey());
        intent.setRequestHash(requestHash);
        intent.setProviderKey(providerKey);
        intent.setProvider(provider);
        intent.setType(taskType);
        intent.setStatus("PREPARED");
        intent.setModel(model.getModelId());
        intent.setModelName(model.getDisplayName());
        intent.setPrompt(command.prompt());
        intent.setParams(JsonUtils.toJsonString(parameters));
        var prepared = intentStore.prepare(intent);
        if (prepared.created()) {
            activityEventService.publish(
                    intent.getUserId(), "task.created", intent.getProjectId(),
                    intent.getExecutionRunId(), prepared.task().getId(), null, null, null, null,
                    toView(prepared.task()));
            afterCommit(() -> taskExecutor.resumeIntent(prepared.task().getId()));
        }
        return toView(prepared.task());
    }

    private Long submitIndependent(AigcTaskSubmitCommand command) {
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.UNAUTHORIZED, "当前用户未登录"));
        var parameters = new LinkedHashMap<>(parseParameters(command.parametersJson()));
        return switch (command.taskType().toUpperCase()) {
            case "IMAGE" -> submitImage(userId, command, parameters);
            case "VIDEO" -> submitVideo(userId, command, parameters);
            case "MODEL_3D" -> submitModel3d(userId, command, parameters);
            default ->
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST,
                            "不支持的 AIGC 子任务类型: " + command.taskType());
        };
    }

    private String capability(String taskType) {
        return switch (taskType) {
            case "IMAGE" -> CapabilityRoutingContext.CAP_IMAGE_GEN;
            case "VIDEO" -> CapabilityRoutingContext.CAP_VIDEO_GEN;
            case "MODEL_3D" -> CapabilityRoutingContext.CAP_MODEL_3D;
            case "MUSIC" -> CapabilityRoutingContext.CAP_MUSIC_GEN;
            case "VOICE" -> CapabilityRoutingContext.CAP_SPEECH_TTS;
            default ->
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "不支持的 AIGC 子任务类型: " + taskType);
        };
    }

    private String creditCategory(String taskType) {
        return switch (taskType) {
            case "IMAGE" -> CreditTransactionCategoryEnum.IMAGE_GEN.getCode();
            case "VIDEO" -> CreditTransactionCategoryEnum.VIDEO.getCode();
            case "MODEL_3D" -> CreditTransactionCategoryEnum.MODEL_3D.getCode();
            case "MUSIC" -> CreditTransactionCategoryEnum.MUSIC.getCode();
            case "VOICE" -> CreditTransactionCategoryEnum.SPEECH_TTS.getCode();
            default ->
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "不支持的 AIGC 子任务类型: " + taskType);
        };
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }


    private Long submitImage(
            Long userId, AigcTaskSubmitCommand command, Map<String, Object> parameters) {
        return taskService.submitImageTask(
                userId,
                new ImageTaskRequest(
                        command.prompt(),
                        command.modelId(),
                        integer(parameters.get("width")),
                        integer(parameters.get("height")),
                        text(parameters.get("negativePrompt")),
                        integer(parameters.get("seed")),
                        bool(parameters.get("promptExtend")),
                        integer(parameters.get("imageCount")),
                        longList(parameters.get("imageFileIds")),
                        text(parameters.get("quality")),
                        text(parameters.get("format")),
                        text(parameters.get("background")),
                        text(parameters.get("contentModeration")),
                        null,
                        null,
                        command.prompt(),
                        null,
                        null,
                        command.projectId()));
    }

    private Long submitVideo(
            Long userId, AigcTaskSubmitCommand command, Map<String, Object> parameters) {
        return taskService.submitVideoTask(
                userId,
                new VideoTaskRequest(
                        command.prompt(),
                        command.modelId(),
                        command.projectId(),
                        text(parameters.get("resolution")),
                        integer(parameters.get("duration")),
                        text(parameters.get("ratio")),
                        integer(parameters.get("seed")),
                        text(parameters.get("imageMode")),
                        longValue(parameters.get("imageFileId")),
                        longList(parameters.get("referenceImageFileIds")),
                        stringList(parameters.get("referenceVideoUrls")),
                        stringList(parameters.get("referenceAudioUrls")),
                        text(parameters.get("audioSetting")),
                        bool(parameters.get("promptExtend")),
                        bool(parameters.get("generateAudio"))));
    }

    private Long submitModel3d(
            Long userId, AigcTaskSubmitCommand command, Map<String, Object> parameters) {
        return taskService.submit3dTask(
                userId,
                command.prompt(),
                command.modelId(),
                text(parameters.getOrDefault("mode", "text")),
                text(parameters.get("textureQuality")),
                command.projectId());
    }

    @Override
    @Transactional
    public AigcTaskView cancel(Long taskId, String reason) {
        var task = requireTaskEntity(taskId);
        if (!java.util.Set.of("PREPARED", "SUBMITTING", "PENDING", "RUNNING")
                .contains(task.getStatus())) {
            return toView(task);
        }
        task.setStatus("FAIL");
        task.setErrorMsg(reason == null ? "执行已取消" : reason);
        task.setVersion(task.getVersion() + 1);
        taskRepository.saveAndFlush(task);
        return toView(task);
    }

    @Override
    public AigcTaskView requireTask(Long taskId) {
        return toView(requireTaskEntity(taskId));
    }

    private AigcTask requireTaskEntity(Long taskId) {
        var task =
                taskRepository
                        .findById(taskId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "AIGC 任务不存在"));
        if (operatorContext.currentOwnerId().filter(task.getUserId()::equals).isEmpty()) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "AIGC 任务不存在");
        }
        return task;
    }

    private AigcTaskView toView(AigcTask task) {
        return new AigcTaskView(
                task.getId(),
                task.getExecutionRunId(),
                task.getType(),
                task.getStatus(),
                task.getOutputMediaVersionId(),
                task.getErrorMsg());
    }

    private Map<String, Object> parseParameters(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(this::longValue).filter(java.util.Objects::nonNull).toList();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Boolean bool(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : null;
    }
}
