package com.xuejiai.aaf.module.ai.aigc.task.job;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
import com.xuejiai.aaf.common.enums.aigc.AigcTaskTypeEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.image.process.ImageProcessService;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcGeneratedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 图像处理异步任务状态定时同步。
 *
 * <p>轮询 PENDING 的 IMAGE_PROCESS 任务（如 SEGMENT_HD_COMMON_IMAGE）。 QPS 限速由 {@link
 * ImageProcessService#queryTask} 内的令牌桶统一控制，Job 无需限速。
 *
 * <p>{@link ImageProcessService} 依赖阿里云 OSS 配置，未配置时为 null，调度方法提前返回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageProcessTaskSyncJob {

    private final AigcTaskRepository taskRepo;
    private final AigcTaskService aigcTaskService;

    /** 依赖阿里云配置，未配置时为 null */
    @Autowired(required = false)
    private ImageProcessService imageProcessService;

    private final FileStoragePort fileService;
    private final AigcMediaApi mediaApi;
    private final AigcActivityEventService eventService;
    private final AigcTaskMapper taskMapper;
    private final PermissionExecutionService permissionExecutionService;

    @OrgIgnore
    @Scheduled(fixedDelay = 10_000)
    public void sync() {
        if (imageProcessService == null) return;
        var tasks =
                taskRepo.findByStatusAndType(
                        AigcTaskStatusEnum.PENDING.getCode(),
                        AigcTaskTypeEnum.IMAGE_PROCESS.getCode());
        if (tasks.isEmpty()) return;

        for (var task : tasks) {
            try {
                processOne(task);
            } catch (Exception e) {
                log.warn(
                        "[ImageProcessSync] 同步失败: taskId={}, err={}",
                        task.getId(),
                        e.getMessage(),
                        e);
            }
        }
    }

    private void processOne(AigcTask task) {
        String jobId = task.getProviderTaskId();
        if (jobId == null) return;

        String method = extractMethod(task.getParams());
        log.debug(
                "[ImageProcessSync] 轮询任务: taskId={}, jobId={}, method={}, params={}",
                task.getId(),
                jobId,
                method,
                task.getParams());
        var result = imageProcessService.queryTask(jobId, method);

        if ("SUCCESS".equals(result.status())) {
            permissionExecutionService.runAsOwner(
                    task.getUserId(),
                    "图像处理完成",
                    () -> {
                        String resultUrl = result.resultUrl();
                        String ext = guessExt(resultUrl);
                        String path = "aigc/image_process/%s.%s".formatted(UUID.randomUUID(), ext);
                        var storedFile =
                                fileService.uploadFromUrl(
                                        resultUrl, path, imageContentType(ext), task.getUserId());
                        task.setProviderResult(JsonUtils.toJsonString(result));
                        var media =
                                mediaApi.createFromGeneratedFile(
                                        new AigcGeneratedMediaCommand(
                                                task.getUserId(),
                                                "AI抠图-" + task.getId(),
                                                AigcMediaType.IMAGE,
                                                storedFile,
                                                null,
                                                null,
                                                task.getId(),
                                                task.getProjectId(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                task.getParams()));
                        task.setOutputMediaVersionId(media.currentVersion().id());
                        task.setStatus(AigcTaskStatusEnum.SUCCESS.getCode());
                        task.setUpdateTime(LocalDateTime.now());
                        taskRepo.save(task);

                        try {
                            eventService.publish(
                                    task.getUserId(), "task.completed", taskMapper.toVO(task));
                        } catch (Exception ignored) {
                        }

                        log.info(
                                "[ImageProcessSync] 任务完成: taskId={}, mediaVersionId={}",
                                task.getId(),
                                task.getOutputMediaVersionId());
                    });

        } else if ("FAILED".equals(result.status())) {
            log.warn("[ImageProcessSync] 任务失败: taskId={}", task.getId());
            permissionExecutionService.runAsOwner(
                    task.getUserId(),
                    "图像处理失败",
                    () -> aigcTaskService.failTask(jobId, result.errorMessage()));
        }
    }

    private static String imageContentType(String extension) {
        return "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
    }

    /** 从任务参数 JSON 中提取 method 字段，用于路由到正确的类目客户端查询异步结果。 */
    private static String extractMethod(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) return null;
        try {
            var node = JsonUtils.readTree(paramsJson);
            return node.path("method").asString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String guessExt(String url) {
        if (url == null) return "png";
        try {
            String path = url.split("\\?")[0];
            int dot = path.lastIndexOf('.');
            String ext = dot >= 0 ? path.substring(dot + 1).toLowerCase() : "";
            return (!ext.isEmpty() && ext.length() <= 5) ? ext : "png";
        } catch (Exception e) {
            return "png";
        }
    }
}
