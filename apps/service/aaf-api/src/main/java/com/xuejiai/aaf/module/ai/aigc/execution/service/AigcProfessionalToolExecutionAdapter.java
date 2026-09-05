package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcToolExecutionPort;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskApi;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskSubmitCommand;

import lombok.RequiredArgsConstructor;

/** 将版本化 Tool 目标适配到现有专业文案与媒体生成能力。 */
@Component
@RequiredArgsConstructor
public class AigcProfessionalToolExecutionAdapter implements AigcToolExecutionPort {

    private static final String IMAGE_GENERATE = "aigc.image.generate";
    private static final String IMAGE_EDIT = "aigc.image.edit";
    private static final String VIDEO_GENERATE = "aigc.video.generate";
    private static final Set<String> IMAGE_ARGUMENT_KEYS =
            Set.of(
                    "width",
                    "height",
                    "negativePrompt",
                    "seed",
                    "promptExtend",
                    "imageCount",
                    "quality",
                    "format",
                    "background",
                    "contentModeration");
    private static final Set<String> VIDEO_ARGUMENT_KEYS =
            Set.of(
                    "resolution",
                    "duration",
                    "ratio",
                    "seed",
                    "imageMode",
                    "referenceVideoUrls",
                    "referenceAudioUrls",
                    "audioSetting",
                    "promptExtend",
                    "generateAudio");

    private final AigcTaskApi taskApi;
    private final AigcMediaApi mediaApi;

    @Override
    public ToolResult execute(Command command) {
        return switch (command.targetRef()) {
            case IMAGE_GENERATE -> executeImage(command, false);
            case IMAGE_EDIT -> executeImage(command, true);
            case VIDEO_GENERATE -> executeVideo(command);
            default ->
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "不支持的 Tool 动作目标: " + command.targetRef());
        };
    }

    private ToolResult executeImage(Command command, boolean edit) {
        var parameters = allowedArguments(command, IMAGE_ARGUMENT_KEYS);
        var fileIds = attachmentFileIds(command);
        if (edit || !fileIds.isEmpty()) {
            parameters.put("imageFileIds", fileIds);
        }
        if ("project.cover.generate".equals(command.actionKey())) {
            parameters.put("imageCount", 1);
        }
        return submit(command, "IMAGE", parameters);
    }

    private ToolResult executeVideo(Command command) {
        var parameters = allowedArguments(command, VIDEO_ARGUMENT_KEYS);
        var fileIds = attachmentFileIds(command);
        if (!fileIds.isEmpty()) {
            parameters.put("imageFileId", fileIds.getFirst());
            parameters.put("referenceImageFileIds", fileIds);
        }
        return submit(command, "VIDEO", parameters);
    }

    private ToolResult submit(Command command, String taskType, Map<String, Object> parameters) {
        var task =
                taskApi.submit(
                        new AigcTaskSubmitCommand(
                                command.executionRunId(),
                                command.projectId(),
                                command.projectObjectId(),
                                taskType,
                                text(command.input().get("requestedModelId")),
                                command.prompt(),
                                JsonUtils.toJsonString(parameters),
                                command.idempotencyKey()));
        return new ToolResult("", List.of(task.id()));
    }

    private LinkedHashMap<String, Object> allowedArguments(
            Command command, Set<String> allowedKeys) {
        var result = new LinkedHashMap<String, Object>();
        var value = command.input().get("actionArguments");
        if (!(value instanceof Map<?, ?> arguments)) {
            return result;
        }
        arguments.forEach(
                (key, argument) -> {
                    if (key instanceof String name && allowedKeys.contains(name)) {
                        result.put(name, argument);
                    }
                });
        return result;
    }

    private List<Long> attachmentFileIds(Command command) {
        return attachmentMediaVersionIds(command).stream()
                .map(
                        mediaVersionId ->
                                mediaApi.getByVersionId(mediaVersionId, command.userId())
                                        .currentVersion()
                                        .fileId())
                .toList();
    }

    private List<Long> attachmentMediaVersionIds(Command command) {
        var values = command.input().get("attachmentMediaVersionIds");
        if (!(values instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .toList();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
