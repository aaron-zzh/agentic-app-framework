package com.xuejiai.aaf.module.ai.aigc.task;

import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.intelligent.ai.image.AsyncImageGenerationService.AsyncImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AIGC 统一任务服务——汇聚图像/视频/3D 模型三类生成任务，统一管理状态流转和 OSS 存储。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigcTaskService {

    private static final String TYPE_IMAGE = "IMAGE";
    private static final String TYPE_VIDEO = "VIDEO";
    private static final String TYPE_MODEL3D = "MODEL_3D";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";

    private static final String EVENT_CREATED = "task.created";
    private static final String EVENT_COMPLETED = "task.completed";
    private static final String EVENT_FAILED = "task.failed";

    private final AigcTaskRepository taskRepo;
    private final AigcTaskEventService eventService;
    private final StorageService storageService;
    private final MediaAssetService mediaAssetService;
    private final ImageServiceFactory imageServiceFactory;
    private final CapabilityRouter capabilityRouter;

    // ========== 提交任务 ==========

    /**
     * 提交图像生成任务，根据 model 路由到 wanx 或 midjourney。
     *
     * @param userId 用户 ID
     * @param prompt 生成描述
     * @param model 模型名（null 则由路由决策）
     * @param width 宽度（像素）
     * @param height 高度（像素）
     * @return 统一任务 ID
     */
    @Transactional
    public Long submitImageTask(
            Long userId, String prompt, String model, Integer width, Integer height) {
        var ctx =
                CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_IMAGE_GEN, model);
        String resolvedModel = capabilityRouter.resolve(ctx);

        var task = buildTask(userId, TYPE_IMAGE, prompt, resolvedModel);
        task.setParams(
                "{\"width\":%d,\"height\":%d}"
                        .formatted(width != null ? width : 1024, height != null ? height : 1024));
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        // 异步提交到底层服务
        var asyncService = imageServiceFactory.getAsyncService(resolvedModel);
        var req =
                new AsyncImageRequest(
                        prompt,
                        resolvedModel,
                        width != null ? width : 1024,
                        height != null ? height : 1024);
        Thread.startVirtualThread(
                () -> {
                    try {
                        String thirdTaskId = asyncService.submitTask(req);
                        task.setTaskId(thirdTaskId);
                        task.setStatus(STATUS_RUNNING);
                        taskRepo.save(task);
                    } catch (Exception e) {
                        log.error("[submitImageTask] 提交失败: taskId={}", task.getId(), e);
                        failTask(task.getTaskId(), e.getMessage());
                    }
                });

        return task.getId();
    }

    /**
     * 提交视频生成任务（占位实现，底层接入具体服务时扩展）。
     *
     * @param userId 用户 ID
     * @param prompt 生成描述
     * @param model 模型名
     * @return 统一任务 ID
     */
    @Transactional
    public Long submitVideoTask(Long userId, String prompt, String model) {
        var task = buildTask(userId, TYPE_VIDEO, prompt, model);
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));
        log.info("[submitVideoTask] 视频生成任务已创建: taskId={}, model={}", task.getId(), model);
        // TODO: 接入视频生成底层服务（如通义万象视频生成）
        return task.getId();
    }

    /**
     * 提交 3D 模型生成任务（占位实现，底层接入具体服务时扩展）。
     *
     * @param userId 用户 ID
     * @param prompt 生成描述
     * @param model 模型名
     * @return 统一任务 ID
     */
    @Transactional
    public Long submit3dTask(Long userId, String prompt, String model) {
        var task = buildTask(userId, TYPE_MODEL3D, prompt, model);
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));
        log.info("[submit3dTask] 3D 模型生成任务已创建: taskId={}, model={}", task.getId(), model);
        // TODO: 接入 3D 生成底层服务
        return task.getId();
    }

    // ========== 任务完成/失败回调 ==========

    /**
     * 任务完成回调：下载第三方 resultUrl → 上传 OSS → 写入 MediaAsset → 推送 SSE。
     *
     * @param thirdTaskId 第三方任务 ID
     * @param resultUrl 第三方结果 URL
     */
    @Transactional
    public void completeTask(String thirdTaskId, String resultUrl) {
        var task = taskRepo.findByTaskId(thirdTaskId).orElse(null);
        if (task == null) {
            log.warn("[completeTask] 任务不存在: thirdTaskId={}", thirdTaskId);
            return;
        }

        task.setResultUrl(resultUrl);

        // 下载结果并上传 OSS
        String ossUrl = uploadToOss(resultUrl, task.getType(), task.getId());
        task.setOssUrl(ossUrl);
        task.setStatus(STATUS_SUCCESS);
        task.setUpdateTime(LocalDateTime.now());
        taskRepo.save(task);

        // 写入素材库
        saveToMediaAsset(task, ossUrl);

        // 推送 SSE 完成事件
        eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
        log.info("[completeTask] 任务完成: taskId={}, ossUrl={}", task.getId(), ossUrl);
    }

    /**
     * 任务失败回调：更新状态 → 推送 SSE。
     *
     * @param thirdTaskId 第三方任务 ID（或本系统任务 ID 字符串）
     * @param errorMsg 失败原因
     */
    @Transactional
    public void failTask(String thirdTaskId, String errorMsg) {
        if (thirdTaskId == null) return;
        var task = taskRepo.findByTaskId(thirdTaskId).orElse(null);
        if (task == null) {
            log.warn("[failTask] 任务不存在: thirdTaskId={}", thirdTaskId);
            return;
        }
        task.setStatus(STATUS_FAIL);
        task.setErrorMsg(errorMsg);
        task.setUpdateTime(LocalDateTime.now());
        taskRepo.save(task);

        eventService.push(task.getUserId(), EVENT_FAILED, toVO(task));
        log.info("[failTask] 任务失败: taskId={}, reason={}", task.getId(), errorMsg);
    }

    // ========== 查询 ==========

    /**
     * 分页查询用户任务列表。
     *
     * @param userId 用户 ID
     * @param pageNo 页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<AigcTaskVO> pageByUser(Long userId, int pageNo, int pageSize) {
        var pageable =
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<AigcTask> page = taskRepo.findByUserIdOrderByCreateTimeDesc(userId, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    // ========== 内部方法 ==========

    private AigcTask buildTask(Long userId, String type, String prompt, String model) {
        var task = new AigcTask();
        task.setUserId(userId);
        task.setType(type);
        task.setStatus(STATUS_PENDING);
        task.setPrompt(prompt);
        task.setModel(model);
        // 简单从 model 名推断 provider
        if (model != null) {
            if (model.toLowerCase().contains("midjourney")) {
                task.setProvider("midjourney");
            } else if (model.toLowerCase().contains("wanx")
                    || model.toLowerCase().contains("wan-x")) {
                task.setProvider("wanx");
            } else {
                task.setProvider(model);
            }
        }
        return task;
    }

    /** 从 URL 下载内容并上传到 OSS，返回 OSS 访问 URL。 */
    private String uploadToOss(String url, String type, Long taskId) {
        try {
            String ext = guessExtension(url, type);
            String filename = "aigc/%s/%d.%s".formatted(type.toLowerCase(), taskId, ext);
            String contentType = guessContentType(type);

            try (InputStream is = URI.create(url).toURL().openStream()) {
                String key = storageService.upload(is, filename, contentType);
                return storageService.getUrl(key);
            }
        } catch (Exception e) {
            log.warn("[uploadToOss] 上传 OSS 失败，回退使用原始 URL: taskId={}, url={}", taskId, url, e);
            return url; // 上传失败时降级使用原始 URL
        }
    }

    /** 将完成的任务写入素材库 */
    private void saveToMediaAsset(AigcTask task, String ossUrl) {
        try {
            var dto =
                    new SaveFromGenerationDTO(
                            "AI生成-" + task.getType() + "-" + task.getId(),
                            toMediaAssetType(task.getType()),
                            ossUrl,
                            null,
                            "{\"prompt\":\"%s\",\"model\":\"%s\"}"
                                    .formatted(
                                            task.getPrompt() != null
                                                    ? task.getPrompt().replace("\"", "'")
                                                    : "",
                                            task.getModel() != null ? task.getModel() : ""),
                            null,
                            null,
                            null);
            mediaAssetService.saveFromGeneration(task.getUserId(), dto);
        } catch (Exception e) {
            log.warn("[saveToMediaAsset] 写入素材库失败: taskId={}", task.getId(), e);
        }
    }

    private MediaAssetType toMediaAssetType(String type) {
        return switch (type) {
            case TYPE_VIDEO -> MediaAssetType.VIDEO;
            case TYPE_MODEL3D -> MediaAssetType.MODEL_3D;
            default -> MediaAssetType.IMAGE;
        };
    }

    private String guessExtension(String url, String type) {
        if (url != null && url.contains(".")) {
            String path = url.split("\\?")[0];
            String[] parts = path.split("\\.");
            String ext = parts[parts.length - 1].toLowerCase();
            if (ext.length() <= 5) return ext;
        }
        return switch (type) {
            case TYPE_VIDEO -> "mp4";
            case TYPE_MODEL3D -> "glb";
            default -> "png";
        };
    }

    private String guessContentType(String type) {
        return switch (type) {
            case TYPE_VIDEO -> "video/mp4";
            case TYPE_MODEL3D -> "model/gltf-binary";
            default -> "image/png";
        };
    }

    AigcTaskVO toVO(AigcTask task) {
        return new AigcTaskVO(
                task.getId(),
                task.getUserId(),
                task.getType(),
                task.getStatus(),
                task.getProvider(),
                task.getModel(),
                task.getPrompt(),
                task.getTaskId(),
                task.getResultUrl(),
                task.getOssUrl(),
                task.getErrorMsg(),
                task.getCreateTime(),
                task.getUpdateTime());
    }
}
