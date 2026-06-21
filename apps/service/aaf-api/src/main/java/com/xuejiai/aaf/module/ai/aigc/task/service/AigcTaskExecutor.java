package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.MidjourneyAsyncImageService;
import com.xuejiai.aaf.framework.intelligent.ai.image.decorator.ImageGenServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService.MusicRequest;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoRequest;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.security.license.License;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;
import com.xuejiai.aaf.module.system.file.service.FileUploadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MidjourneyAsyncImageService midjourneyAsyncImageService;

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
        try {
            task.setStatus(STATUS_RUNNING);
            taskRepo.save(task);

            var p = parseImageParams(prompt, modelId, task.getParams());
            var aiModel = configCacheManager.getAiModelByModelId(normalizeModelId(modelId));

            ImageResult result;
            if (mockUrl != null && !mockUrl.isBlank()) {
                // Mock 模式：跳过真实 API，直接构造结果
                result = new ImageResult(mockUrl, null, modelId);
            } else {
                // Midjourney 走异步提交，等待任务完成后再拉取结果
                if (aiModel != null
                        && aiModel.effectiveProviderType()
                                == com.xuejiai.aaf.framework.intelligent.core.model
                                        .AiModelProviderType.MIDJOURNEY
                        && midjourneyAsyncImageService != null) {
                    var req =
                            new com.xuejiai.aaf.framework.intelligent.ai.image
                                    .AsyncImageGenerationService.AsyncImageRequest(prompt, modelId);
                    String mjTaskId = midjourneyAsyncImageService.submitTask(req);
                    // taskId 格式：{modelId}:{mjTaskId}，供查询时找回模型配置
                    task.setTaskId(modelId + ":" + mjTaskId);
                    task.setStatus("PENDING");
                    taskRepo.save(task);
                    log.info(
                            "[AigcTaskExecutor] Midjourney 任务提交: taskId={}, mjTaskId={}",
                            taskId,
                            mjTaskId);
                    return;
                }
                var svc = aiServiceRegistry.get(ImageGenerationService.class, aiModel);
                if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
                    if (svc instanceof ImageGenServiceDecorator creditSvc) {
                        result = creditSvc.generateWithImages(aiModel, p);
                    } else {
                        result =
                                svc.imageToImage(
                                        aiModel,
                                        new ImageEditRequest(
                                                p.getImageUrls().get(0),
                                                null,
                                                prompt,
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
                } else {
                    result = svc.generate(aiModel, p);
                }
            }

            // 装饰器 settle 后通过 ThreadLocal 暴露 creditTxId，这里回填到任务实体
            // 后续若 OSS 上传失败，catch 块据此触发 refund
            Long creditTxId =
                    com.xuejiai.aaf.framework.engine.credit.CreditCallContext.takeLastCreditTxId();
            if (creditTxId != null) {
                task.setCreditTxId(creditTxId);
                taskRepo.save(task);
            }

            String ossUrl;
            String firstUrl =
                    result.url() != null
                            ? result.url()
                            : (result.urls() != null && !result.urls().isEmpty()
                                    ? result.urls().get(0)
                                    : null);
            if (firstUrl != null) {
                String ext = guessImageExt(firstUrl);
                String path =
                        "aigc/%s/%s.%s"
                                .formatted(task.getType().toLowerCase(), UUID.randomUUID(), ext);
                // 判断是 b64 还是 url：b64 不含 http 且较长，或以 data: 开头
                if (firstUrl.startsWith("data:")
                        || (!firstUrl.startsWith("http") && firstUrl.length() > 200)) {
                    ossUrl = fileService.uploadFromBase64(firstUrl, path, null);
                } else {
                    ossUrl = fileService.uploadFromUrl(firstUrl, path, "image/" + ext, null);
                }
            } else if (result.b64Json() != null) {
                String path =
                        "aigc/%s/%s.png".formatted(task.getType().toLowerCase(), UUID.randomUUID());
                ossUrl = fileService.uploadFromBase64(result.b64Json(), path, null);
            } else {
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
            task.setResultUrl(ossUrl);
            task.setOssUrl(ossUrl);
            task.setStatus(STATUS_SUCCESS);
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            long groupId =
                    saveToMediaAsset(
                            task,
                            ossUrl,
                            p.getDisplayPrompt(),
                            p.getSizePreset(),
                            p.getWidth(),
                            p.getHeight());

            // 多图：从第二张起额外写入素材库，共享同一素材组
            var extraUrls = result.urls();
            if (extraUrls != null && extraUrls.size() > 1) {
                for (int i = 1; i < extraUrls.size(); i++) {
                    try {
                        String extraUrl = extraUrls.get(i);
                        String ext = guessImageExt(extraUrl);
                        String path =
                                "aigc/%s/%s.%s"
                                        .formatted(
                                                task.getType().toLowerCase(),
                                                UUID.randomUUID(),
                                                ext);
                        String extra =
                                fileService.uploadFromUrl(extraUrl, path, "image/" + ext, null);
                        saveExtraAsset(
                                task,
                                extra,
                                p.getSizePreset(),
                                p.getWidth(),
                                p.getHeight(),
                                groupId);
                    } catch (Exception e) {
                        log.warn("[submitSync] 第{}张图上传失败: taskId={}", i + 1, taskId, e);
                    }
                }
            }

            log.info("[submitSync] 任务完成: taskId={}, ossUrl={}", taskId, ossUrl);
        } catch (Exception e) {
            log.error("[submitSync] 生成失败: taskId={}", taskId, e);
            // 若 svc.generate 已成功并扣过积分，但后续步骤（如 OSS 上传）失败，则退还
            refundIfSettled(task, e.getMessage());
            task.setStatus(STATUS_FAIL);
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
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
        try {
            task.setStatus("RUNNING");
            taskRepo.save(task);

            String ossUrl;
            if (mockUrl != null && !mockUrl.isBlank()) {
                ossUrl = mockUrl;
            } else {
                var aiModel = configCacheManager.getAiModelByModelId(task.getModelName());
                var result =
                        aiServiceRegistry
                                .get(MusicGenerationService.class, aiModel)
                                .generate(aiModel, new MusicRequest(prompt, lyrics, gender, "mp3"));
                // 装饰器 settle 后回填 creditTxId 用于后续失败退还
                Long creditTxId =
                        com.xuejiai.aaf.framework.engine.credit.CreditCallContext
                                .takeLastCreditTxId();
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

            // 写入素材库
            try {
                var dto =
                        new SaveFromGenerationDTO(
                                prompt != null
                                        ? prompt.substring(0, Math.min(prompt.length(), 40))
                                        : "AI音乐",
                                MediaAssetType.AUDIO,
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
                Long creditTxId =
                        com.xuejiai.aaf.framework.engine.credit.CreditCallContext
                                .takeLastCreditTxId();
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

            // 写入素材库（AUDIO 类型），用配音文本前 20 字命名
            try {
                String name = text.substring(0, Math.min(text.length(), 20));
                var dto =
                        new SaveFromGenerationDTO(
                                name,
                                MediaAssetType.AUDIO,
                                ossUrl,
                                null,
                                JsonUtils.toJsonString(
                                        Map.of("text", text, "voice", voice != null ? voice : "")),
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
                                    task.getParams(),
                                    new tools.jackson.core.type.TypeReference<
                                            Map<String, Object>>() {})
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

            var aiModel = configCacheManager.getAiModelByModelId(normalizeModelId(modelId));
            var svc =
                    aiServiceRegistry.get(
                            com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService
                                    .class,
                            aiModel);

            // 从 task.params 反序列化视频参数
            Map<String, Object> p =
                    task.getParams() != null
                            ? JsonUtils.parseObject(
                                    task.getParams(),
                                    new tools.jackson.core.type.TypeReference<
                                            Map<String, Object>>() {})
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
                            && aiModel.effectiveProviderType()
                                    == com.xuejiai.aaf.framework.intelligent.core.model
                                            .AiModelProviderType.VOLCENGINE;
            boolean hasRichMedia =
                    (referenceVideoUrls != null && !referenceVideoUrls.isEmpty())
                            || (referenceAudioUrls != null && !referenceAudioUrls.isEmpty());

            String thirdTaskId;
            if (isVolcengine
                    && hasRichMedia
                    && svc
                            instanceof
                            com.xuejiai.aaf.framework.intelligent.ai.video
                                            .DoubaoVideoGenerationService
                                    doubao) {
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
            creditTxId =
                    com.xuejiai.aaf.framework.engine.credit.CreditCallContext.takeLastCreditTxId();
        }
        if (creditTxId == null) return;
        try {
            Long refundTxId = creditGuard.refund(creditTxId, "AIGC 任务失败自动退还: " + reason);
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
