package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
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
    private final AigcProjectApi projectApi;
    private final OperatorContext operatorContext;

    @Override
    @Transactional
    public AigcTaskView submit(AigcTaskSubmitCommand command) {
        if (command == null || command.taskType() == null || command.taskType().isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "AIGC 子任务类型不能为空");
        }
        var userId = operatorContext.currentOwnerId().orElseThrow();
        if (command.projectId() != null) {
            projectApi.lockForGeneratedResource(command.projectId(), userId);
        }
        var parameters = parseParameters(command.parametersJson());
        var taskId =
                switch (command.taskType().toUpperCase()) {
                    case "IMAGE" -> submitImage(userId, command, parameters);
                    case "VIDEO" -> submitVideo(userId, command, parameters);
                    case "MODEL_3D" -> submitModel3d(userId, command, parameters);
                    default ->
                            throw new BusinessException(
                                    GlobalErrorCode.BAD_REQUEST,
                                    "不支持的 AIGC 子任务类型: " + command.taskType());
                };
        return requireTask(taskId);
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
        if (!"PENDING".equals(task.getStatus()) && !"RUNNING".equals(task.getStatus())) {
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

    @Override
    @Transactional
    public void deleteProjectResources(Long projectId) {
        taskRepository.softDeleteGenerationHistoryByProjectId(projectId);
        var tasks = taskRepository.findByProjectIdOrderByIdAsc(projectId);
        tasks.forEach(
                task -> {
                    if ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())) {
                        task.setStatus("FAIL");
                        task.setErrorMsg("项目已删除");
                        task.setVersion(task.getVersion() + 1);
                    }
                });
        taskRepository.saveAllAndFlush(tasks);
        taskRepository.deleteAll(tasks);
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
                null,
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
