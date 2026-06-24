package com.xuejiai.aaf.module.ai.aigc.task.job;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.ai.image.process.ImageProcessService;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskEventService;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;
import com.xuejiai.aaf.module.system.file.service.FileUploadService;

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

    private final FileUploadService fileService;
    private final MediaAssetService mediaAssetService;
    private final AigcTaskEventService eventService;
    private final AigcTaskMapper taskMapper;
    private final PermissionExecutionService permissionExecutionService;

    @Scheduled(fixedDelay = 10_000)
    public void sync() {
        if (imageProcessService == null) return;
        var tasks = taskRepo.findByStatusAndType("PENDING", "IMAGE_PROCESS");
        if (tasks.isEmpty()) return;

        for (var task : tasks) {
            try {
                processOne(task);
            } catch (Exception e) {
                log.warn(
                        "[ImageProcessSync] 同步失败: taskId={}, err={}", task.getId(), e.getMessage());
            }
        }
    }

    private void processOne(AigcTask task) {
        String jobId = task.getTaskId();
        if (jobId == null) return;

        // queryTask 内部有令牌桶限速（2 QPS），此处无需额外控制
        var result = imageProcessService.queryTask(jobId);

        if ("SUCCESS".equals(result.status())) {
            permissionExecutionService.runAsOwner(
                    task.getUserId(),
                    "图像处理完成",
                    () -> {
                        String resultUrl = result.resultUrl();
                        String ext = guessExt(resultUrl);
                        String path = "aigc/image_process/%s.%s".formatted(UUID.randomUUID(), ext);
                        String ossUrl;
                        try {
                            ossUrl =
                                    fileService.uploadFromUrl(
                                            resultUrl, path, "image/" + ext, null);
                        } catch (Exception e) {
                            throw new RuntimeException("OSS 上传失败: " + e.getMessage(), e);
                        }
                        task.setResultUrl(ossUrl);
                        task.setOssUrl(ossUrl);
                        task.setStatus("SUCCESS");
                        task.setUpdateTime(LocalDateTime.now());
                        taskRepo.save(task);

                        try {
                            mediaAssetService.saveFromGeneration(
                                    task.getUserId(),
                                    new SaveFromGenerationDTO(
                                            "AI抠图-" + task.getId(),
                                            MediaAssetType.IMAGE,
                                            ossUrl,
                                            null,
                                            task.getParams(),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true,
                                            task.getModelName(),
                                            task.getProvider(),
                                            task.getProjectId()));
                        } catch (Exception e) {
                            log.warn("[ImageProcessSync] 写入素材库失败: taskId={}", task.getId(), e);
                        }

                        try {
                            eventService.push(
                                    task.getUserId(), "task.completed", taskMapper.toVO(task));
                        } catch (Exception ignored) {
                        }

                        log.info(
                                "[ImageProcessSync] 任务完成: taskId={}, ossUrl={}",
                                task.getId(),
                                ossUrl);
                    });

        } else if ("FAILED".equals(result.status())) {
            log.warn("[ImageProcessSync] 任务失败: taskId={}", task.getId());
            permissionExecutionService.runAsOwner(
                    task.getUserId(),
                    "图像处理失败",
                    () -> aigcTaskService.failTask(jobId, result.errorMessage()));
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
