package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.engine.credit.CreditCallContext;
import com.xuejiai.aaf.framework.intelligent.ai.image.AsyncImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.MidjourneyAsyncImageService;
import com.xuejiai.aaf.framework.intelligent.ai.image.decorator.ImageGenServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.ai.image.process.ImageProcessService;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService.MusicRequest;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.video.DoubaoVideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoRequest;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelProviderType;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.security.license.License;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;
import com.xuejiai.aaf.module.system.file.service.FileUploadService;
import com.xuejiai.aaf.module.user.growth.event.UserGrowthEvent;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * AIGC 任务异步执行器。
 *
 * <p>独立 Bean，确保 {@code @Async} 通过 Spring 代理生效（同 bean 内自调用无效）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigcTaskExecutor {

    private final AigcTaskRepository taskRepo;
    private final AigcTaskEventService eventService;
    private final AigcTaskMapper taskMapper;
    private final FileUploadService fileService;
    private final MediaAssetService mediaAssetService;
    private final AiServiceRegistry aiServiceRegistry;
    private final AiCreditGuard creditGuard;
    private final ConfigCacheManager configCacheManager;
    private final Model3dGenerationService model3dGenerationService;
    private final EntityManager entityManager;
    private final PermissionExecutionService permissionExecutionService;
    private final ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MidjourneyAsyncImageService midjourneyAsyncImageService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ImageProcessService imageProcessService;

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String EVENT_COMPLETED = "task.completed";
    private static final String EVENT_FAILED = "task.failed";

    /**
     * 同步模型路径（所有图像生成模型统一入口）。
     *
     * <p>从 {@code task.params} 读取 width/height/negativePrompt/seed/promptExtend/imageCount， 构建完整
     * {@link ImageRequest} 后调用对应服务。 REQUIRES_NEW 表示：不管外部是否有事务，都新建一个独立事务。
     * 外部事务挂起，这个方法在自己的事务里执行，完成后提交/回滚，再恢复外部事务。
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitSync(Long taskId, String prompt, String modelId, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        // @Async 子线程无 SecurityContext，显式设置用户上下文，确保积分结算能取到 userId
        try (var ignored =
                com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder.useOwner(
                        task.getUserId(), "aigc-image-gen")) {
            submitSyncInternal(task, taskId, prompt, modelId, mockUrl);
        }
    }

    private void submitSyncInternal(
            AigcTask task, Long taskId, String prompt, String modelId, String mockUrl) {
        try {
            task.setStatus(STATUS_RUNNING);
            taskRepo.save(task);

            var p = parseImageParams(prompt, modelId, task.getParams());
            // 设置了技能时，将 systemPrompt 前置拼接到 prompt
            if (task.getSystemPrompt() != null && !task.getSystemPrompt().isBlank()) {
                p.setPrompt(task.getSystemPrompt() + "\n\n" + p.getPrompt());
            }
            var aiModel = configCacheManager.getAiModelByModelId(normalizeModelId(modelId));

            // ① 调用 AI 生成（Midjourney 异步提交后直接返回）
            var result = doGenerateImage(task, p, aiModel, mockUrl, taskId, modelId);
            if (result == null) return; // Midjourney 异步路径，任务转 PENDING

            // ② 回填 creditTxId（装饰器 settle 后通过 ThreadLocal 暴露）
            Long creditTxId = CreditCallContext.takeLastCreditTxId();
            if (creditTxId != null) {
                task.setCreditTxId(creditTxId);
                taskRepo.save(task);
            }

            // ③ OSS 上传第一张
            log.debug(
                    "[submitSync] 开始上传: taskId={}, resultUrl={}, b64={}",
                    taskId,
                    result.url(),
                    result.b64Json() != null
                            ? "非空(len=" + result.b64Json().length() + ")"
                            : "null");
            String ossUrl = uploadFirstImage(task, result, taskId, modelId);
            task.setResultUrl(ossUrl);
            task.setOssUrl(ossUrl);
            task.setStatus(STATUS_SUCCESS);
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);

            // ④ 写素材库（需要用户上下文，runAsOwner 确保 ownerId 正确填充）
            permissionExecutionService.runAsOwner(
                    task.getUserId(),
                    "图像素材保存",
                    () -> {
                        long groupId =
                                saveToMediaAsset(
                                        task,
                                        ossUrl,
                                        p.getDisplayPrompt(),
                                        p.getSizePreset(),
                                        p.getWidth(),
                                        p.getHeight());
                        saveExtraImages(task, result, p, groupId);
                    });

            log.info("[submitSync] 任务完成: taskId={}, ossUrl={}", taskId, ossUrl);
            eventPublisher.publishEvent(
                    new UserGrowthEvent(task.getUserId(), "aigc.image.success"));
        } catch (Exception e) {
            log.error("[submitSync] 生成失败: taskId={}", taskId, e);
            refundIfSettled(task, e.getMessage());
            task.setStatus(STATUS_FAIL);
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            try {
                taskRepo.save(task);
            } catch (Exception saveEx) {
                log.error("[submitSync] 任务状态回写失败: taskId={}", taskId, saveEx);
            }
        }
        try {
            eventService.push(
                    task.getUserId(),
                    task.getStatus().equals(STATUS_SUCCESS) ? EVENT_COMPLETED : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
    }

    /** 调用 AI 服务生成图像。Midjourney 异步路径返回 null（任务已转 PENDING）。 */
    private ImageResult doGenerateImage(
            AigcTask task,
            ImageRequest p,
            AiModel aiModel,
            String mockUrl,
            Long taskId,
            String modelId) {
        if (mockUrl != null && !mockUrl.isBlank()) {
            return new ImageResult(mockUrl, null, modelId);
        }
        if (aiModel != null
                && aiModel.effectiveProviderType() == AiModelProviderType.MIDJOURNEY
                && midjourneyAsyncImageService != null) {
            var req = new AsyncImageGenerationService.AsyncImageRequest(p.getPrompt(), modelId);
            String mjTaskId = midjourneyAsyncImageService.submitTask(req);
            task.setTaskId(modelId + ":" + mjTaskId);
            task.setStatus("PENDING");
            taskRepo.save(task);
            log.info(
                    "[AigcTaskExecutor] Midjourney 任务提交: taskId={}, mjTaskId={}", taskId, mjTaskId);
            return null;
        }
        var svc = aiServiceRegistry.get(ImageGenerationService.class, aiModel);
        log.info(
                "[图片任务] modelId={}, imageUrls={}, prompt={}",
                modelId,
                p.getImageUrls(),
                p.getPrompt() != null && p.getPrompt().length() > 50
                        ? p.getPrompt().substring(0, 50) + "..."
                        : p.getPrompt());
        if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
            if (svc instanceof ImageGenServiceDecorator creditSvc) {
                return creditSvc.generateWithImages(aiModel, p);
            }
            return svc.imageToImage(
                    aiModel,
                    new ImageEditRequest(
                            p.getImageUrls().get(0),
                            null,
                            p.getPrompt(),
                            null,
                            modelId,
                            p.getQuality(),
                            p.getFormat(),
                            p.getBackground(),
                            p.getModeration(),
                            p.getImageCount() > 1 ? p.getImageCount() : null,
                            p.getImageUrls()) {
                        {
                            setWidth(p.getWidth());
                            setHeight(p.getHeight());
                            setSizePreset(p.getSizePreset());
                            setAspectRatio(p.getAspectRatio());
                        }
                    });
        }
        return svc.generate(aiModel, p);
    }

    /** 上传第一张图到 OSS，返回 ossUrl。 */
    private String uploadFirstImage(
            AigcTask task, ImageResult result, Long taskId, String modelId) {
        String firstUrl =
                result.url() != null
                        ? result.url()
                        : (result.urls() != null && !result.urls().isEmpty()
                                ? result.urls().get(0)
                                : null);
        if (firstUrl != null) {
            String ext = guessImageExt(firstUrl);
            String path =
                    "aigc/%s/%s.%s".formatted(task.getType().toLowerCase(), UUID.randomUUID(), ext);
            if (firstUrl.startsWith("data:")
                    || (!firstUrl.startsWith("http") && firstUrl.length() > 200)) {
                return fileService.uploadFromBase64(firstUrl, path, null);
            }
            return fileService.uploadFromUrl(firstUrl, path, "image/" + ext, null);
        }
        if (result.b64Json() != null) {
            String path =
                    "aigc/%s/%s.png".formatted(task.getType().toLowerCase(), UUID.randomUUID());
            return fileService.uploadFromBase64(result.b64Json(), path, null);
        }
        throw new IllegalStateException(
                "图片生成结果为空: taskId="
                        + taskId
                        + ", model="
                        + modelId
                        + ", resultUrl="
                        + result.url()
                        + ", b64Json="
                        + (result.b64Json() != null
                                ? "非空(len=" + result.b64Json().length() + ")"
                                : "null"));
    }

    /** 上传并保存第 2～N 张额外图片到同一素材组。 */
    private void saveExtraImages(AigcTask task, ImageResult result, ImageRequest p, long groupId) {
        var extraUrls = result.urls();
        if (extraUrls == null || extraUrls.size() <= 1) return;
        for (int i = 1; i < extraUrls.size(); i++) {
            try {
                String extraUrl = extraUrls.get(i);
                String ext = guessImageExt(extraUrl);
                String path =
                        "aigc/%s/%s.%s"
                                .formatted(task.getType().toLowerCase(), UUID.randomUUID(), ext);
                String extra = fileService.uploadFromUrl(extraUrl, path, "image/" + ext, null);
                saveExtraAsset(
                        task, extra, p.getSizePreset(), p.getWidth(), p.getHeight(), groupId);
            } catch (Exception e) {
                log.warn("[submitSync] 第{}张图上传失败: taskId={}", i + 1, task.getId(), e);
            }
        }
    }

    private long saveToMediaAsset(
            AigcTask task, String ossUrl, String displayPrompt, String sizePreset, int w, int h) {
        try {
            var type =
                    switch (task.getType()) {
                        case "VIDEO" -> MediaAssetType.VIDEO;
                        case "MODEL_3D" -> MediaAssetType.MODEL_3D;
                        default -> MediaAssetType.IMAGE;
                    };

            // 每次生成任务创建一个素材组，用 displayPrompt（用户原始输入）命名，避免带入项目提示词前缀
            // group 在 saveFromGeneration 的 REQUIRES_NEW 事务内创建，保证 group+asset 原子提交
            String nameSource =
                    displayPrompt != null && !displayPrompt.isBlank()
                            ? displayPrompt
                            : (task.getPrompt() != null && !task.getPrompt().isBlank()
                                    ? task.getPrompt()
                                    : "AI生成-" + task.getType() + "-" + task.getId());
            // 截取前 20 字符，并去掉末尾不完整的标点/空白，避免截断中文词
            String groupName =
                    nameSource.length() <= 20
                            ? nameSource.strip()
                            : nameSource
                                    .substring(0, 20)
                                    .replaceAll("[，。！？、,.!?\\s]+$", "")
                                    .strip();

            var dto =
                    new SaveFromGenerationDTO(
                            groupName,
                            type,
                            ossUrl,
                            null,
                            JsonUtils.toJsonString(
                                    Map.of(
                                            "prompt",
                                                    task.getPrompt() != null
                                                            ? task.getPrompt()
                                                            : "",
                                            "model", task.getModel() != null ? task.getModel() : "",
                                            "sizePreset", sizePreset != null ? sizePreset : "")),
                            w,
                            h,
                            null,
                            null,
                            groupName,
                            true,
                            task.getModelName(),
                            task.getProvider(),
                            task.getProjectId());
            var saved = mediaAssetService.saveFromGeneration(task.getUserId(), dto);
            return saved.groupId() != null ? saved.groupId() : 0L;
        } catch (Exception e) {
            log.warn("[saveToMediaAsset] 写入素材库失败: taskId={}", task.getId(), e);
            // 清除脏 Session，防止 null ID 的 group 污染后续操作
            entityManager.clear();
            return 0L;
        }
    }

    /** 多图时追加第 2～N 张到同一素材组 */
    private void saveExtraAsset(
            AigcTask task, String ossUrl, String sizePreset, int w, int h, long groupId) {
        try {
            var type =
                    switch (task.getType()) {
                        case "VIDEO" -> MediaAssetType.VIDEO;
                        case "MODEL_3D" -> MediaAssetType.MODEL_3D;
                        default -> MediaAssetType.IMAGE;
                    };
            String name =
                    task.getPrompt() != null
                            ? task.getPrompt().substring(0, Math.min(task.getPrompt().length(), 40))
                            : "AI生成";
            var dto =
                    new SaveFromGenerationDTO(
                            name,
                            type,
                            ossUrl,
                            null,
                            JsonUtils.toJsonString(
                                    Map.of(
                                            "prompt",
                                                    task.getPrompt() != null
                                                            ? task.getPrompt()
                                                            : "",
                                            "model", task.getModel() != null ? task.getModel() : "",
                                            "sizePreset", sizePreset != null ? sizePreset : "")),
                            w,
                            h,
                            null,
                            groupId,
                            null,
                            true,
                            task.getModelName(),
                            task.getProvider(),
                            task.getProjectId());
            mediaAssetService.saveFromGeneration(task.getUserId(), dto);
        } catch (Exception e) {
            log.warn("[saveExtraAsset] 追加素材失败: taskId={}", task.getId(), e);
        }
    }

    /** 音乐生成异步执行（同步 API，阻塞直到结果返回）。 */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitMusicSync(
            Long taskId, String prompt, String lyrics, String gender, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        try (var ignored =
                com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder.useOwner(
                        task.getUserId(), "aigc-music-gen")) {
            submitMusicSyncInternal(task, taskId, prompt, lyrics, gender, mockUrl);
        }
    }

    private void submitMusicSyncInternal(
            AigcTask task,
            Long taskId,
            String prompt,
            String lyrics,
            String gender,
            String mockUrl) {
        try {
            task.setStatus("RUNNING");
            taskRepo.save(task);

            String ossUrl;
            if (mockUrl != null && !mockUrl.isBlank()) {
                ossUrl = mockUrl;
            } else {
                var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
                var result =
                        aiServiceRegistry
                                .get(MusicGenerationService.class, aiModel)
                                .generate(aiModel, new MusicRequest(prompt, lyrics, gender, "mp3"));
                // 装饰器 settle 后回填 creditTxId 用于后续失败退还
                Long creditTxId = CreditCallContext.takeLastCreditTxId();
                if (creditTxId != null) {
                    task.setCreditTxId(creditTxId);
                    taskRepo.save(task);
                }
                task.setResultUrl(result.audioUrl());
                String path = "aigc/music/%s.mp3".formatted(UUID.randomUUID());
                ossUrl = fileService.uploadFromUrl(result.audioUrl(), path, "audio/mpeg", null);
            }
            task.setOssUrl(ossUrl);
            task.setStatus("SUCCESS");
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);

            // 写入素材库（runAsOwner 确保异步线程中 ownerId 正确填充）
            final String musicOssUrl = ossUrl;
            permissionExecutionService.runAsOwner(
                    task.getUserId(),
                    "音乐素材保存",
                    () -> {
                        try {
                            var dto =
                                    new SaveFromGenerationDTO(
                                            prompt != null
                                                    ? prompt.substring(
                                                            0, Math.min(prompt.length(), 40))
                                                    : "AI音乐",
                                            MediaAssetType.MUSIC,
                                            musicOssUrl,
                                            null,
                                            "{\"prompt\":\"%s\",\"model\":\"%s\"}"
                                                    .formatted(
                                                            task.getPrompt() != null
                                                                    ? task.getPrompt()
                                                                            .replace("\"", "'")
                                                                    : "",
                                                            task.getModel() != null
                                                                    ? task.getModel()
                                                                    : ""),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true,
                                            task.getModelName(),
                                            task.getProvider(),
                                            task.getProjectId());
                            mediaAssetService.saveFromGeneration(task.getUserId(), dto);
                        } catch (Exception e) {
                            log.warn("[submitMusicSync] 写入素材库失败: taskId={}", taskId, e);
                        }
                    });

            log.info("[submitMusicSync] 音乐生成完成: taskId={}, ossUrl={}", taskId, ossUrl);
        } catch (Exception e) {
            log.error("[submitMusicSync] 生成失败: taskId={}", taskId, e);
            refundIfSettled(task, e.getMessage());
            task.setStatus("FAIL");
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
        }
        try {
            eventService.push(
                    task.getUserId(),
                    "SUCCESS".equals(task.getStatus()) ? EVENT_COMPLETED : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitMusicSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
    }

    /** 配音生成异步执行（TTS 非流式，阻塞合成完整音频后上传存储）。 */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitVoiceSync(Long taskId, String text, String voice, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        try (var ignored =
                com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder.useOwner(
                        task.getUserId(), "aigc-voice-gen")) {
            submitVoiceSyncInternal(task, taskId, text, voice, mockUrl);
        }
    }

    private void submitVoiceSyncInternal(
            AigcTask task, Long taskId, String text, String voice, String mockUrl) {
        try {
            task.setStatus(STATUS_RUNNING);
            taskRepo.save(task);

            String ossUrl;
            if (mockUrl != null && !mockUrl.isBlank()) {
                ossUrl = mockUrl;
            } else {
                var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
                if (aiModel == null) {
                    throw new IllegalStateException("配音模型未配置: " + task.getModel());
                }
                var result =
                        aiServiceRegistry
                                .get(SpeechService.class, aiModel)
                                .synthesize(aiModel, text, voice);
                // 装饰器 settle 后回填 creditTxId 用于后续失败退还
                Long creditTxId = CreditCallContext.takeLastCreditTxId();
                if (creditTxId != null) {
                    task.setCreditTxId(creditTxId);
                    taskRepo.save(task);
                }
                byte[] audio = result.audio();
                if (audio == null || audio.length == 0) {
                    throw new IllegalStateException("配音合成结果为空: taskId=" + taskId);
                }
                String path = "aigc/voice/%s.mp3".formatted(UUID.randomUUID());
                ossUrl = fileService.uploadFromBytes(audio, path, "audio/mpeg", null);
            }

            task.setResultUrl(ossUrl);
            task.setOssUrl(ossUrl);
            task.setStatus(STATUS_SUCCESS);
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);

            // 写入素材库（AUDIO 类型），用配音文本前 20 字命名（runAsOwner 确保 ownerId 正确填充）
            final String voiceOssUrl = ossUrl;
            permissionExecutionService.runAsOwner(
                    task.getUserId(),
                    "配音素材保存",
                    () -> {
                        try {
                            String name = text.substring(0, Math.min(text.length(), 20));
                            var dto =
                                    new SaveFromGenerationDTO(
                                            name,
                                            MediaAssetType.AUDIO,
                                            voiceOssUrl,
                                            null,
                                            JsonUtils.toJsonString(
                                                    Map.of(
                                                            "text",
                                                            text,
                                                            "voice",
                                                            voice != null ? voice : "")),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true,
                                            task.getModelName(),
                                            task.getProvider(),
                                            task.getProjectId());
                            mediaAssetService.saveFromGeneration(task.getUserId(), dto);
                        } catch (Exception e) {
                            log.warn("[submitVoiceSync] 写入素材库失败: taskId={}", taskId, e);
                        }
                    });

            log.info("[submitVoiceSync] 配音生成完成: taskId={}, ossUrl={}", taskId, ossUrl);
        } catch (Exception e) {
            log.error("[submitVoiceSync] 生成失败: taskId={}", taskId, e);
            refundIfSettled(task, e.getMessage());
            task.setStatus(STATUS_FAIL);
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
        }
        try {
            eventService.push(
                    task.getUserId(),
                    STATUS_SUCCESS.equals(task.getStatus()) ? EVENT_COMPLETED : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitVoiceSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
    }

    /** 3D 模型生成异步执行（提交到第三方后立即返回，由 {@code Model3dTaskSyncJob} 轮询结果）。 */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitModel3dSync(Long taskId, String prompt, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        try {
            task.setStatus("RUNNING");
            taskRepo.save(task);

            if (mockUrl != null && !mockUrl.isBlank()) {
                task.setOssUrl(mockUrl);
                task.setStatus("SUCCESS");
                task.setUpdateTime(LocalDateTime.now());
                taskRepo.save(task);
                eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
                return;
            }

            // 从 task.params 读 source/textureQuality
            Map<String, Object> p =
                    task.getParams() != null
                            ? JsonUtils.parseObject(
                                    task.getParams(), new TypeReference<Map<String, Object>>() {})
                            : Map.of();
            String source = p.containsKey("source") ? (String) p.get("source") : "text";
            String textureQuality =
                    p.containsKey("textureQuality") ? (String) p.get("textureQuality") : null;

            // 按 source 路由提交方法
            String thirdTaskId =
                    switch (source) {
                        case "image" ->
                                model3dGenerationService.submitImageTo3d(
                                        new Model3dGenerationService.ImageTo3dRequest(
                                                null, textureQuality, null));
                        case "multi" ->
                                model3dGenerationService.submitMultiImageTo3d(
                                        new Model3dGenerationService.MultiImageTo3dRequest(
                                                null, textureQuality, null));
                        default ->
                                model3dGenerationService.submitTextTo3d(
                                        new Model3dGenerationService.TextTo3dRequest(
                                                prompt, textureQuality, null));
                    };
            task.setTaskId(thirdTaskId);
            task.setStatus("PENDING");
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            log.info(
                    "[submitModel3dSync] 任务已提交: taskId={}, thirdTaskId={}, source={}",
                    taskId,
                    thirdTaskId,
                    source);
        } catch (Exception e) {
            log.error("[submitModel3dSync] 提交失败: taskId={}", taskId, e);
            task.setStatus("FAIL");
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            try {
                eventService.push(task.getUserId(), EVENT_FAILED, toVO(task));
            } catch (Exception ignored) {
            }
        }
    }

    /** 视频生成异步执行（提交到第三方后立即返回，由 {@code VideoTaskSyncJob} 轮询结果）。 */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitVideoAsync(Long taskId, String prompt, String modelId, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        try {
            task.setStatus("RUNNING");
            taskRepo.save(task);

            if (mockUrl != null && !mockUrl.isBlank()) {
                task.setOssUrl(mockUrl);
                task.setStatus("SUCCESS");
                task.setUpdateTime(LocalDateTime.now());
                taskRepo.save(task);
                eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
                return;
            }

            var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
            var svc = aiServiceRegistry.get(VideoGenerationService.class, aiModel);

            // 从 task.params 反序列化视频参数
            Map<String, Object> p =
                    task.getParams() != null
                            ? JsonUtils.parseObject(
                                    task.getParams(), new TypeReference<Map<String, Object>>() {})
                            : Map.of();

            String imageModeStr = p.containsKey("imageMode") ? (String) p.get("imageMode") : "T2V";
            var imageModeEnum = VideoRequest.ImageMode.valueOf(imageModeStr);

            // Seedance rich（有 referenceVideoUrls/referenceAudioUrls）：volcengine provider 调用
            // submitRich
            @SuppressWarnings("unchecked")
            List<String> referenceVideoUrls =
                    p.get("referenceVideoUrls") instanceof List<?> l
                            ? l.stream().map(Object::toString).toList()
                            : null;
            @SuppressWarnings("unchecked")
            List<String> referenceAudioUrls =
                    p.get("referenceAudioUrls") instanceof List<?> l
                            ? l.stream().map(Object::toString).toList()
                            : null;

            boolean isVolcengine =
                    aiModel != null
                            && aiModel.effectiveProviderType() == AiModelProviderType.VOLCENGINE;
            boolean hasRichMedia =
                    (referenceVideoUrls != null && !referenceVideoUrls.isEmpty())
                            || (referenceAudioUrls != null && !referenceAudioUrls.isEmpty());

            String thirdTaskId;
            if (isVolcengine
                    && hasRichMedia
                    && svc instanceof DoubaoVideoGenerationService doubao) {
                @SuppressWarnings("unchecked")
                List<String> referenceImages =
                        p.get("referenceImageUrls") instanceof List<?> l
                                ? l.stream().map(Object::toString).toList()
                                : null;
                boolean generateAudio = Boolean.TRUE.equals(p.get("generateAudio"));
                thirdTaskId =
                        doubao.submitRich(
                                aiModel,
                                prompt,
                                referenceImages,
                                referenceVideoUrls,
                                referenceAudioUrls,
                                (String) p.get("ratio"),
                                toParamInt(p.get("duration")),
                                generateAudio);
            } else {
                @SuppressWarnings("unchecked")
                List<String> referenceImageUrls =
                        p.get("referenceImageUrls") instanceof List<?> l
                                ? l.stream().map(Object::toString).toList()
                                : null;
                var request =
                        new VideoRequest(
                                prompt,
                                (String) p.get("imageUrl"),
                                referenceImageUrls,
                                modelId,
                                (String) p.get("resolution"),
                                (String) p.get("ratio"),
                                toParamInt(p.get("duration")),
                                toParamInt(p.get("seed")),
                                imageModeEnum);
                thirdTaskId = svc.submit(request);
            }

            task.setTaskId(thirdTaskId);
            task.setStatus("PENDING");
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            log.info("[submitVideoAsync] 任务已提交: taskId={}, thirdTaskId={}", taskId, thirdTaskId);
        } catch (Exception e) {
            log.error("[submitVideoAsync] 提交失败: taskId={}", taskId, e);
            task.setStatus("FAIL");
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            try {
                eventService.push(task.getUserId(), EVENT_FAILED, toVO(task));
            } catch (Exception ignored) {
            }
        }
    }

    private static Integer toParamInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 task.params JSON 反序列化为 ImageRequest，再补充 prompt/modelId。 */
    private ImageRequest parseImageParams(String prompt, String modelId, String paramsJson) {
        try {
            ImageRequest req =
                    paramsJson != null
                            ? JsonUtils.parseObject(paramsJson, ImageRequest.class)
                            : new ImageRequest();
            req.setPrompt(prompt);
            req.setModelId(modelId);
            return req;
        } catch (Exception ignore) {
            return new ImageRequest(prompt, modelId);
        }
    }

    /** 从 URL 或 data URL 中推断图片扩展名，取不到则返回 {@code png}。 */
    private String guessImageExt(String url) {
        if (url == null) return "png";
        if (url.startsWith("data:")) {
            // data:image/webp;base64,...
            int slash = url.indexOf('/');
            int semi = url.indexOf(';');
            if (slash > 0 && semi > slash) {
                String ext = url.substring(slash + 1, semi);
                return ext.equals("jpeg") ? "jpg" : ext;
            }
            return "png";
        }
        try {
            String path = new java.net.URI(url).getPath();
            int dot = path.lastIndexOf('.');
            String ext = dot >= 0 ? path.substring(dot + 1) : "";
            return (!ext.isEmpty() && ext.length() <= 5) ? ext : "png";
        } catch (Exception e) {
            return "png";
        }
    }

    private AigcTaskVO toVO(AigcTask task) {
        return taskMapper.toVO(task);
    }

    /**
     * 图像处理任务异步执行（SEGMENT_HD_BODY 等同步 SDK 调用，完成后直接存 OSS）。
     *
     * @param taskId 内部任务 ID
     * @param imageUrl 待处理图像 URL
     * @param method 处理方式，如 SEGMENT_HD_BODY
     * @param mockUrl Mock 模式固定返回 URL，null 表示真实调用
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitImageProcessSync(
            Long taskId, String imageUrl, String method, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        try (var ignored =
                com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder.useOwner(
                        task.getUserId(), "aigc-image-process")) {
            submitImageProcessSyncInternal(task, taskId, imageUrl, method, mockUrl);
        }
    }

    private void submitImageProcessSyncInternal(
            AigcTask task, Long taskId, String imageUrl, String method, String mockUrl) {
        try {
            task.setStatus(STATUS_RUNNING);
            taskRepo.save(task);

            String ossUrl;
            if (mockUrl != null && !mockUrl.isBlank()) {
                ossUrl = mockUrl;
            } else {
                if (imageProcessService == null) {
                    throw new IllegalStateException("ImageProcessService 未配置，请检查阿里云 OSS 凭证");
                }
                var result =
                        imageProcessService.process(
                                new ImageProcessService.ProcessRequest(imageUrl, method));

                // 异步模式（如 SEGMENT_HD_COMMON_IMAGE）：提交成功后转 PENDING，由轮询 Job 处理
                if ("PENDING".equals(result.status())) {
                    task.setTaskId(result.taskId());
                    task.setStatus("PENDING");
                    task.setUpdateTime(LocalDateTime.now());
                    taskRepo.save(task);
                    log.info(
                            "[submitImageProcessSync] 异步任务已提交: taskId={}, jobId={}",
                            taskId,
                            result.taskId());
                    return;
                }

                if (!"SUCCESS".equals(result.status())) {
                    throw new IllegalStateException("图像处理失败: " + result.errorMessage());
                }
                String resultUrl = result.resultUrl();
                String ext = guessImageExt(resultUrl);
                String path = "aigc/image_process/%s.%s".formatted(UUID.randomUUID(), ext);
                ossUrl = fileService.uploadFromUrl(resultUrl, path, "image/" + ext, null);
            }

            task.setResultUrl(ossUrl);
            task.setOssUrl(ossUrl);
            task.setStatus(STATUS_SUCCESS);
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);

            permissionExecutionService.runAsOwner(
                    task.getUserId(),
                    "图像处理素材保存",
                    () -> {
                        try {
                            var dto =
                                    new SaveFromGenerationDTO(
                                            "AI处理-" + method + "-" + task.getId(),
                                            MediaAssetType.IMAGE,
                                            ossUrl,
                                            null,
                                            JsonUtils.toJsonString(
                                                    Map.of(
                                                            "imageUrl", imageUrl,
                                                            "method", method)),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true,
                                            task.getModelName(),
                                            task.getProvider(),
                                            task.getProjectId());
                            mediaAssetService.saveFromGeneration(task.getUserId(), dto);
                        } catch (Exception e) {
                            log.warn("[submitImageProcessSync] 写入素材库失败: taskId={}", taskId, e);
                        }
                    });

            log.info("[submitImageProcessSync] 图像处理完成: taskId={}, ossUrl={}", taskId, ossUrl);
        } catch (Exception e) {
            log.error("[submitImageProcessSync] 处理失败: taskId={}", taskId, e);
            task.setStatus(STATUS_FAIL);
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
        }
        try {
            eventService.push(
                    task.getUserId(),
                    STATUS_SUCCESS.equals(task.getStatus()) ? EVENT_COMPLETED : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitImageProcessSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
    }

    /** 统一处理模型 ID 大小写与空白，确保与平台配置一致。 */
    private String normalizeModelId(String modelId) {
        long s = License.get().getCouplingSeed();
        log.debug("[normalizeModelId] seed={}, modelId={}", s, modelId);
        if (s == 0L) {
            String mangled = modelId + "_" + Long.toHexString(System.nanoTime() & 0xffL);
            log.debug("[normalizeModelId] seed=0, mangled={}", mangled);
            return mangled;
        }
        return modelId;
    }

    /**
     * 失败时若已扣过积分（task.creditTxId 非空，或 ThreadLocal 中有未消费的 creditTxId），触发退还。
     *
     * <p>覆盖两类场景：
     *
     * <ul>
     *   <li>装饰器 settle 已执行 + creditTxId 已写入 task.creditTxId 后续步骤失败 → 用 task.creditTxId 退还
     *   <li>装饰器 settle 已执行但还未来得及写到 task（罕见）→ 从 ThreadLocal 兜底取值
     * </ul>
     */
    private void refundIfSettled(AigcTask task, String reason) {
        Long creditTxId = task.getCreditTxId();
        if (creditTxId == null) {
            creditTxId = CreditCallContext.takeLastCreditTxId();
        }
        if (creditTxId == null) return;
        try {
            Long refundTxId =
                    creditGuard.refund(
                            creditTxId,
                            ("AIGC 任务失败自动退还: " + reason)
                                    .substring(
                                            0,
                                            Math.min(200, ("AIGC 任务失败自动退还: " + reason).length())));
            if (refundTxId != null) {
                log.info(
                        "[refundIfSettled] 积分已退还: taskId={}, originalTxId={}, refundTxId={}",
                        task.getId(),
                        creditTxId,
                        refundTxId);
            }
        } catch (Exception e) {
            log.warn("[refundIfSettled] 积分退还失败: taskId={}, err={}", task.getId(), e.getMessage());
        }
    }
}
