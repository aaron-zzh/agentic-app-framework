package com.xuejiai.aaf.module.content.service.action;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_EXECUTION_TARGET_INVALID;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.content.ContentExecutionStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentExecutionTargetTypeEnum;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.copywriting.CopywritingService;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;
import com.xuejiai.aaf.module.content.repository.ContentExecutionRunRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tool 动作执行器，接通文案流式生成与 AIGC 图像任务。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class ContentToolActionExecutor implements ContentActionExecutor {

    private static final String COPYWRITING_GENERATE = "copywriting.generate";
    private static final String IMAGE_GENERATE = "aigc.image.generate";
    private static final String IMAGE_EDIT = "aigc.image.edit";

    private final CopywritingService copywritingService;
    private final AigcTaskService aigcTaskService;
    private final ContentExecutionRunRepository runRepository;
    private final ContentObjectVersionCandidateService candidateService;
    private final OperatorContext operatorContext;

    @Override
    public boolean supports(String targetType) {
        return ContentExecutionTargetTypeEnum.TOOL.getCode().equals(targetType);
    }

    @Override
    public void execute(ContentActionContext context) {
        var targetRef = context.binding().getTargetRef();
        switch (targetRef) {
            case COPYWRITING_GENERATE -> executeCopywriting(context);
            case IMAGE_GENERATE -> executeImage(context, false);
            case IMAGE_EDIT -> executeImage(context, true);
            default -> throw exception(CONTENT_EXECUTION_TARGET_INVALID);
        }
    }

    private void executeCopywriting(ContentActionContext context) {
        var run = context.executionRun();
        markRunning(run);
        try {
            var chunks =
                    copywritingService
                            .generate(
                                    null,
                                    "custom",
                                    effectivePrompt(context),
                                    null,
                                    "medium",
                                    null,
                                    safeList(context.command().attachmentRefs()))
                            .collectList()
                            .block();
            var content = chunks == null ? "" : String.join("", chunks);
            var payload = new LinkedHashMap<String, Object>();
            payload.put("text", content);
            payload.put("actionKey", run.getActionKey());
            var version =
                    candidateService.createCandidate(
                            context.project(), run, payload, List.of(), summarize(content));
            run.setOutputPayload(Map.of("objectVersionId", version.getId(), "text", content));
            run.setStatus(ContentExecutionStatusEnum.SUCCEEDED.getCode());
            run.setEndTime(LocalDateTime.now());
            runRepository.save(run);
        } catch (RuntimeException exception) {
            markFailed(run, exception);
        }
    }

    private void executeImage(ContentActionContext context, boolean edit) {
        var run = context.executionRun();
        markRunning(run);
        try {
            var userId = operatorContext.currentOwnerId().orElseThrow();
            var attachments = safeList(context.command().attachmentRefs());
            var taskId =
                    aigcTaskService.submitImageTask(
                            userId,
                            new ImageTaskRequest(
                                    effectivePrompt(context),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    true,
                                    1,
                                    edit ? attachments : List.of(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    effectivePrompt(context),
                                    null,
                                    null,
                                    context.project().getId()));
            run.setAigcTaskId(taskId);
            run.setOutputPayload(Map.of("aigcTaskId", taskId));
            runRepository.save(run);
        } catch (RuntimeException exception) {
            markFailed(run, exception);
        }
    }

    private void markRunning(com.xuejiai.aaf.module.content.domain.ContentExecutionRun run) {
        run.setStatus(ContentExecutionStatusEnum.RUNNING.getCode());
        run.setStartTime(LocalDateTime.now());
        runRepository.save(run);
    }

    private void markFailed(
            com.xuejiai.aaf.module.content.domain.ContentExecutionRun run,
            RuntimeException exception) {
        run.setStatus(ContentExecutionStatusEnum.FAILED.getCode());
        run.setErrorMessage(exception.getMessage());
        run.setEndTime(LocalDateTime.now());
        runRepository.save(run);
    }

    private String effectivePrompt(ContentActionContext context) {
        var prompt = context.command().prompt();
        return prompt == null || prompt.isBlank() ? context.project().getBrief() : prompt;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "AI 生成内容";
        }
        return content.substring(0, Math.min(content.length(), 200));
    }
}
