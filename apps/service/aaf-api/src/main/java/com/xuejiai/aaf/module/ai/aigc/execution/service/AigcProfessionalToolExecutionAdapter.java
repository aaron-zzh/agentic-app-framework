package com.xuejiai.aaf.module.ai.aigc.execution.service;

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
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.Attachment;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.AttachmentType;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.Input;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.KnowledgeOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.MemoryMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.MemoryOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ModelMode;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.ModelSelection;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.OutputOptions;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionRequest.SkillSelection;

import lombok.RequiredArgsConstructor;

/** 将版本化 Tool 目标适配到现有专业文案与媒体生成能力。 */
@Component
@RequiredArgsConstructor
public class AigcProfessionalToolExecutionAdapter implements AigcToolExecutionPort {

    private static final String COPYWRITING_GENERATE = "copywriting.generate";
    private static final String IMAGE_GENERATE = "aigc.image.generate";
    private static final String IMAGE_EDIT = "aigc.image.edit";

    private final AssistantExecutionService assistantExecutionService;
    private final AigcTaskApi taskApi;
    private final AigcMediaApi mediaApi;

    @Override
    public ToolResult execute(Command command) {
        return switch (command.targetRef()) {
            case COPYWRITING_GENERATE -> executeCopywriting(command);
            case IMAGE_GENERATE -> executeImage(command, false);
            case IMAGE_EDIT -> executeImage(command, true);
            default ->
                    throw new BusinessException(
                            GlobalErrorCode.BAD_REQUEST, "不支持的 Tool 动作目标: " + command.targetRef());
        };
    }

    private ToolResult executeCopywriting(Command command) {
        var request =
                new AssistantExecutionRequest(
                        null,
                        new Input(command.prompt(), Map.of(), imageAttachments(mediaUrls(command))),
                        new SkillSelection("custom"),
                        new KnowledgeOptions(Set.of(), false, 5, 0.2),
                        new ModelSelection(ModelMode.AUTO, null),
                        new MemoryOptions(MemoryMode.DISABLED),
                        new OutputOptions(null, null, null));
        var chunks =
                assistantExecutionService
                        .execute(request)
                        .<String>handle(
                                (event, sink) -> {
                                    var errorMessage = event.payload().get("message");
                                    if (event.payload().containsKey("errorCode")
                                            && errorMessage instanceof String message) {
                                        sink.error(
                                                new BusinessException(
                                                        GlobalErrorCode.INTERNAL_SERVER_ERROR,
                                                        message));
                                        return;
                                    }
                                    var delta = event.payload().get("delta");
                                    if ("MESSAGE_DELTA".equals(event.type())
                                            && delta instanceof String text
                                            && !text.isEmpty()) {
                                        sink.next(text);
                                    }
                                })
                        .collectList()
                        .block();
        return new ToolResult(chunks == null ? "" : String.join("", chunks), List.of());
    }

    private static List<Attachment> imageAttachments(List<String> resourceIds) {
        return resourceIds.stream()
                .map(resourceId -> new Attachment(AttachmentType.IMAGE, null, null, resourceId))
                .toList();
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
