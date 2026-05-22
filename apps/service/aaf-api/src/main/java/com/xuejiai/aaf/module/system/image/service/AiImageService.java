package com.xuejiai.aaf.module.system.image.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.ai.image.AsyncImageGenerationService.AsyncImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.image.MidjourneyImageService;
import com.xuejiai.aaf.framework.intelligent.ai.image.MidjourneyImageService.TaskStatus;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.module.system.image.domain.AiImage;
import com.xuejiai.aaf.module.system.image.repository.AiImageRepository;
import com.xuejiai.aaf.module.system.image.vo.AiImageVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** AI 图像生成业务逻辑。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageService {

    private static final String PLATFORM_MIDJOURNEY = "MIDJOURNEY";
    private static final String PLATFORM_WANX = "WANX";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";

    private final AiImageRepository imageRepository;
    private final MidjourneyImageService midjourneyImageService;
    private final ObjectMapper objectMapper;
    private final CapabilityRouter capabilityRouter;
    private final ImageServiceFactory imageServiceFactory;

    // ========== Midjourney ==========

    /** 提交 imagine 任务 */
    @Transactional
    public Long imagine(Long userId, String prompt, List<String> base64Images) {
        var image = new AiImage();
        image.setUserId(userId);
        image.setPlatform(PLATFORM_MIDJOURNEY);
        image.setPrompt(prompt);
        image.setStatus(STATUS_IN_PROGRESS);
        imageRepository.save(image);

        String taskId;
        try {
            taskId = midjourneyImageService.imagine(prompt, base64Images);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("quota_not_enough")) {
                throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "账户余额不足");
            }
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "图像生成任务提交失败: " + msg);
        }

        image.setTaskId(taskId);
        imageRepository.save(image);
        return image.getId();
    }

    /** 执行后续操作（放大/变体） */
    @Transactional
    public Long action(Long userId, Long imageId, String customId) {
        var image = imageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "图像记录不存在"));
        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "图像记录不存在");
        }
        validateCustomId(image.getButtons(), customId);

        String newTaskId;
        try {
            newTaskId = midjourneyImageService.action(image.getTaskId(), customId);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("quota_not_enough")) {
                throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "账户余额不足");
            }
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "操作提交失败: " + msg);
        }

        var newImage = new AiImage();
        newImage.setUserId(userId);
        newImage.setPlatform(PLATFORM_MIDJOURNEY);
        newImage.setPrompt(image.getPrompt());
        newImage.setWidth(image.getWidth());
        newImage.setHeight(image.getHeight());
        newImage.setStatus(STATUS_IN_PROGRESS);
        newImage.setTaskId(newTaskId);
        imageRepository.save(newImage);
        return newImage.getId();
    }

    /** 定时同步进行中的 Midjourney 任务 */
    public void syncMidjourneyTasks() {
        var images = imageRepository.findByStatusAndPlatform(STATUS_IN_PROGRESS, PLATFORM_MIDJOURNEY);
        if (images.isEmpty()) return;

        var taskIds = images.stream().map(AiImage::getTaskId).collect(Collectors.toList());
        List<TaskStatus> taskList = midjourneyImageService.queryTasks(taskIds);
        var taskMap = taskList.stream().collect(Collectors.toMap(TaskStatus::id, t -> t, (a, b) -> a));

        int count = 0;
        for (var image : images) {
            var task = taskMap.get(image.getTaskId());
            if (task == null) {
                log.warn("[syncMidjourneyTasks] 任务 {} 查询不到进展", image.getTaskId());
                continue;
            }
            updateImageFromTask(image, task);
            count++;
        }
        log.info("[syncMidjourneyTasks] 同步 {} 个 Midjourney 任务", count);
    }

    /** Webhook 回调处理 */
    public void handleNotify(String taskId, String status, String imageUrl, String failReason, String buttonsJson) {
        var imageOpt = imageRepository.findByTaskId(taskId);
        if (imageOpt.isEmpty()) {
            log.warn("[handleNotify] 回调任务 {} 不存在", taskId);
            return;
        }
        var image = imageOpt.get();
        if ("SUCCESS".equals(status)) {
            image.setStatus(STATUS_SUCCESS);
            image.setPicUrl(imageUrl);
            image.setFinishTime(LocalDateTime.now());
        } else if ("FAILURE".equals(status)) {
            image.setStatus(STATUS_FAIL);
            image.setErrorMessage(failReason);
            image.setFinishTime(LocalDateTime.now());
        }
        if (buttonsJson != null) {
            image.setButtons(buttonsJson);
        }
        imageRepository.save(image);
    }

    // ========== 通义万象 wanx ==========

    /**
     * 提交通义万象文生图任务（异步，需轮询）。
     *
     * @param model 模型名（null 时由路由决策）
     */
    @Transactional
    public Long draw(Long userId, String prompt, Integer width, Integer height, String model) {
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_IMAGE_GEN, model);
        String modelId = capabilityRouter.resolve(ctx);

        var image = new AiImage();
        image.setUserId(userId);
        image.setPlatform(PLATFORM_WANX);
        image.setPrompt(prompt);
        image.setWidth(width != null ? width : 1024);
        image.setHeight(height != null ? height : 1024);
        image.setStatus(STATUS_IN_PROGRESS);
        imageRepository.save(image);

        var asyncService = imageServiceFactory.getAsyncService(modelId);
        var request = new AsyncImageRequest(prompt, modelId, image.getWidth(), image.getHeight());
        Thread.startVirtualThread(() -> {
            try {
                String taskId = asyncService.submitTask(request);
                image.setTaskId(taskId);
                imageRepository.save(image);
                log.info("[draw] 任务提交成功: imageId={}, modelId={}, taskId={}", image.getId(), modelId, taskId);
            } catch (Exception e) {
                log.error("[draw] 任务提交失败: imageId={}", image.getId(), e);
                image.setStatus(STATUS_FAIL);
                image.setErrorMessage(e.getMessage());
                image.setFinishTime(LocalDateTime.now());
                imageRepository.save(image);
            }
        });
        return image.getId();
    }

    /** 定时同步通义万象任务状态 */
    public void syncWanxTasks() {
        var images = imageRepository.findByStatusAndPlatform(STATUS_IN_PROGRESS, PLATFORM_WANX);
        if (images.isEmpty()) return;

        var asyncService = imageServiceFactory.getAsyncService(null);
        for (var image : images) {
            if (image.getTaskId() == null) continue;
            try {
                var result = asyncService.queryTask(image.getTaskId());
                switch (result.status()) {
                    case "SUCCEEDED" -> {
                        image.setStatus(STATUS_SUCCESS);
                        image.setPicUrl(result.imageUrl());
                        image.setFinishTime(LocalDateTime.now());
                        imageRepository.save(image);
                    }
                    case "FAILED" -> {
                        image.setStatus(STATUS_FAIL);
                        image.setErrorMessage(result.errorMsg());
                        image.setFinishTime(LocalDateTime.now());
                        imageRepository.save(image);
                    }
                    default -> { /* PENDING/RUNNING，继续等待 */ }
                }
            } catch (Exception e) {
                log.warn("[syncWanxTasks] 查询失败: imageId={}", image.getId(), e);
            }
        }
    }

    // ========== 查询 ==========

    public AiImage getById(Long id) {
        return imageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "图像记录不存在"));
    }

    public List<AiImage> listByUser(Long userId) {
        return imageRepository.findByUserIdAndDeletedFalseOrderByCreateTimeDesc(userId);
    }

    public AiImageVO toVO(AiImage image) {
        return new AiImageVO(
                image.getId(),
                image.getUserId(),
                image.getPlatform(),
                image.getPrompt(),
                image.getWidth(),
                image.getHeight(),
                image.getStatus(),
                image.getTaskId(),
                image.getPicUrl(),
                image.getErrorMessage(),
                image.getButtons(),
                image.getFinishTime(),
                image.getCreateTime());
    }

    // ========== 内部方法 ==========

    private void updateImageFromTask(AiImage image, TaskStatus task) {
        if ("SUCCESS".equals(task.status())) {
            image.setStatus(STATUS_SUCCESS);
            image.setPicUrl(task.imageUrl());
            image.setFinishTime(LocalDateTime.now());
        } else if ("FAILURE".equals(task.status())) {
            image.setStatus(STATUS_FAIL);
            image.setErrorMessage(task.failReason());
            image.setFinishTime(LocalDateTime.now());
        }
        if (task.buttons() != null && !task.buttons().isEmpty()) {
            try {
                image.setButtons(objectMapper.writeValueAsString(task.buttons()));
            } catch (Exception e) {
                log.warn("[updateImageFromTask] 序列化 buttons 失败", e);
            }
        }
        imageRepository.save(image);
    }

    private void validateCustomId(String buttonsJson, String customId) {
        if (buttonsJson == null || buttonsJson.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该图像无可用操作");
        }
        try {
            List<Map<String, Object>> buttons = objectMapper.readValue(buttonsJson, new TypeReference<>() {});
            boolean found = buttons.stream().anyMatch(b -> customId.equals(b.get("customId")));
            if (!found) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "操作按钮不存在: " + customId);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "buttons 解析失败");
        }
    }
}
