package com.xuejiai.aaf.module.content.service.action;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
import com.xuejiai.aaf.common.enums.content.ContentExecutionStatusEnum;
import com.xuejiai.aaf.module.ai.aigc.media.api.MediaApi;
import com.xuejiai.aaf.module.ai.aigc.task.event.AigcTaskTerminalEvent;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.content.repository.ContentExecutionRunRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * Content Studio 图像任务终态回写器。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class ContentAigcTaskTerminalListener {

    private final AigcTaskRepository taskRepository;
    private final MediaApi mediaApi;
    private final ContentExecutionRunRepository runRepository;
    private final ContentProjectRepository projectRepository;
    private final ContentObjectVersionCandidateService candidateService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTerminal(AigcTaskTerminalEvent event) {
        var task = taskRepository.findById(event.taskId()).orElse(null);
        if (task == null) {
            return;
        }
        var run = runRepository.findFirstByAigcTaskIdOrderByIdDesc(task.getId()).orElse(null);
        if (run == null || !ContentExecutionStatusEnum.RUNNING.getCode().equals(run.getStatus())) {
            return;
        }
        if (AigcTaskStatusEnum.SUCCESS.getCode().equals(task.getStatus())) {
            var project = projectRepository.findById(run.getProjectId()).orElse(null);
            if (project == null) {
                return;
            }
            if (task.getOutputMediaVersionId() == null) {
                return;
            }
            var media =
                    mediaApi.getByVersionId(
                            task.getOutputMediaVersionId(), task.getUserId());
            var url = media.currentVersion().url();
            var version =
                    candidateService.createCandidate(
                            project,
                            run,
                            Map.of(
                                    "imageUrl", url,
                                    "aigcTaskId", task.getId(),
                                    "mediaId", media.id(),
                                    "mediaVersionId", task.getOutputMediaVersionId()),
                            List.of(url),
                            "AI 生成图片");
            run.setOutputPayload(
                    Map.of(
                            "aigcTaskId", task.getId(),
                            "objectVersionId", version.getId(),
                            "mediaId", media.id(),
                            "mediaVersionId", task.getOutputMediaVersionId(),
                            "imageUrl", url));
            run.setStatus(ContentExecutionStatusEnum.SUCCEEDED.getCode());
        } else {
            run.setStatus(ContentExecutionStatusEnum.FAILED.getCode());
            run.setErrorMessage(task.getErrorMsg());
        }
        run.setEndTime(LocalDateTime.now());
        runRepository.save(run);
    }
}
