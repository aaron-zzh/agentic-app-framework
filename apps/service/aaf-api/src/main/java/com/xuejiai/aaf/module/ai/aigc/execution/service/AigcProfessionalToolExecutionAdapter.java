package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.List;
import java.util.Map;

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

    private final AigcTaskApi taskApi;
    private final AigcMediaApi mediaApi;

    @Override
    public ToolResult execute(Command command) {
        return switch (command.targetRef()) {
            case IMAGE_GENERATE -> executeImage(command, false);
            case IMAGE_EDIT -> executeImage(command, true);
            default ->
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "不支持的 Tool 动作目标: " + command.targetRef());
        };
    }

    private ToolResult executeImage(Command command, boolean edit) {
        var parameters =
                edit ? JsonUtils.toJsonString(Map.of("sourceImages", mediaUrls(command))) : "{}";
        var task =
                taskApi.submit(
                        new AigcTaskSubmitCommand(
                                command.executionRunId(),
                                command.projectId(),
                                command.projectObjectId(),
                                "IMAGE",
                                null,
                                command.prompt(),
                                parameters,
                                command.idempotencyKey()));
        return new ToolResult("", List.of(task.id()));
    }

    private List<String> mediaUrls(Command command) {
        return attachmentMediaVersionIds(command).stream()
                .map(
                        mediaVersionId ->
                                mediaApi.getByVersionId(mediaVersionId, command.userId())
                                        .currentVersion()
                                        .url())
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
}
