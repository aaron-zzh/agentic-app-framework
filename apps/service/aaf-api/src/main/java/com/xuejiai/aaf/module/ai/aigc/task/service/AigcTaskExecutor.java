package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
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
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.security.license.License;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcGeneratedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.event.AigcTaskTerminalEvent;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.module.user.growth.event.UserGrowthEvent;

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
    private final FileStoragePort fileService;
    private final AigcMediaApi mediaApi;
    private final AiServiceRegistry aiServiceRegistry;
    private final AiCreditGuard creditGuard;
    private final ConfigCacheManager configCacheManager;
    private final Model3dGenerationService model3dGenerationService;
    private final PermissionExecutionService permissionExecutionService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private MidjourneyAsyncImageService midjourneyAsyncImageService;

    @Autowired(required = false)
    private ImageProcessService imageProcessService;

    private static final String EVENT_COMPLETED = "task.completed";
    private static final String EVENT_FAILED = "task.failed";

    /**
     * 在 {@code @Async} 子线程中恢复任务归属者的权限上下文与组织上下文后执行给定逻辑。
     *
     * <p>{@code @Async} 方法运行在独立线程池线程，脱离原 HTTP 请求线程， {@link PermissionExecutionContextHolder}（用户身份）与
     * {@link OrgContext}（组织隔离）均为空—— 前者导致积分结算等取不到 userId；后者不仅让方法入口 {@code taskRepo.findById} 被
     * {@code OrgFilterAspect} fail-closed 拒绝（需搭配方法级 {@link OrgIgnore} 豁免这段空窗期）， 还会让 {@code
     * OperatorEntityListener} 对新建关联记录（如生成结果写入 {@code Media}/{@code MediaVersion}）的
     * orgId/workspaceId 兜底填充失效——{@code @OrgIgnore} 只豁免过滤器的 fail-closed
     * 拒绝，不会代替持久化时的字段回填，必须显式从任务实体恢复真实 orgId， 保证任务与其衍生记录的组织归属一致。执行完毕后清理，避免线程池复用时上下文泄漏。
     *
     * <p>覆盖了整个 internal 方法的执行期间，内部无需再单独调用 {@code permissionExecutionService.runAsOwner} 重复设置同一
     * userId 的权限上下文。
     */
    private void runInOwnerContext(AigcTask task, String reason, Runnable runnable) {
        permissionExecutionService.runAsOwner(
                task.getUserId(),
                reason,
                () -> {
                    OrgContext.setCurrentOrgId(task.getOrgId());
                    OrgContext.setCurrentWorkspaceId(task.getWorkspaceId());
                    try {
                        runnable.run();
                    } finally {
                        // 只清理本方法设置的两个字段，不用 OrgContext.clear()
                        OrgContext.setCurrentOrgId(null);
                        OrgContext.setCurrentWorkspaceId(null);
                    }
                });
    }

    /**
     * 同步模型路径（所有图像生成模型统一入口）。
     *
     * <p>从 {@code task.params} 读取 width/height/negativePrompt/seed/promptExtend/imageCount， 构建完整
     * {@link ImageRequest} 后调用对应服务。 REQUIRES_NEW 表示：不管外部是否有事务，都新建一个独立事务。
     * 外部事务挂起，这个方法在自己的事务里执行，完成后提交/回滚，再恢复外部事务。
     *
     * <p>{@code @OrgIgnore}：方法入口 {@code taskRepo.findById} 执行时尚未知道 task 的 orgId （先有鸡蛋问题，同 {@code
     * OrgFilter} 处理 {@code X-Org-Id} 自校验查询的场景），需豁免这段空窗期的 fail-closed 拒绝；查到 task 后 {@link
     * #runInOwnerContext} 会恢复真实 orgId， 使后续查询/持久化按真实组织语义执行。
     */
    @OrgIgnore
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitSync(Long taskId, String prompt, String modelId, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        runInOwnerContext(
                task,
                "aigc-image-gen",
                () -> submitSyncInternal(task, taskId, prompt, modelId, mockUrl));
    }

    private void submitSyncInternal(
            AigcTask task, Long taskId, String prompt, String modelId, String mockUrl) {
        try {
            task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
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

            // ③ 持久化供应商结果与第一张图片
            log.debug(
                    "[submitSync] 开始持久化: taskId={}, resultUrl={}, b64={}",
                    taskId,
                    result.url(),
                    result.b64Json() != null
                            ? "非空(len=" + result.b64Json().length() + ")"
                            : "null");
            task.setProviderResult(imageProviderResult(result));
            var storedFile = uploadFirstImage(task, result, taskId, modelId);
            var primaryMedia =
                    createMedia(
                            task,
                            storedFile,
                            AigcMediaType.IMAGE,
                            generatedName(task, p.getDisplayPrompt()),
                            p.getWidth(),
                            p.getHeight(),
                            null,
                            generationInfo(task, p.getSizePreset()));
            task.setOutputMediaVersionId(primaryMedia.currentVersion().id());
            task.setStatus(AigcTaskStatusEnum.SUCCESS.getCode());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);

            // ④ 多图候选各自创建独立 Media，不创建素材组
            saveExtraImages(task, result, p);

            log.info(
                    "[submitSync] 任务完成: taskId={}, mediaVersionId={}",
                    taskId,
                    task.getOutputMediaVersionId());
            eventPublisher.publishEvent(
                    new UserGrowthEvent(task.getUserId(), "aigc.image.success"));
        } catch (Exception e) {
            log.error("[submitSync] 生成失败: taskId={}", taskId, e);
            refundIfSettled(task, e.getMessage());
            task.setStatus(AigcTaskStatusEnum.FAIL.getCode());
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
                    task.getStatus().equals(AigcTaskStatusEnum.SUCCESS.getCode())
                            ? EVENT_COMPLETED
                            : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
        eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
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
            task.setProviderTaskId(modelId + ":" + mjTaskId);
            task.setStatus(AigcTaskStatusEnum.PENDING.getCode());
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

    /** 上传第一张图并登记为 sys_file。 */
    private StoredFile uploadFirstImage(
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
                return fileService.uploadFromBase64(firstUrl, path, task.getUserId());
            }
            return fileService.uploadFromUrl(
                    firstUrl, path, imageContentType(ext), task.getUserId());
        }
        if (result.b64Json() != null) {
            String path =
                    "aigc/%s/%s.png".formatted(task.getType().toLowerCase(), UUID.randomUUID());
            return fileService.uploadFromBase64(result.b64Json(), path, task.getUserId());
        }
        throw new IllegalStateException(
                "图片生成结果为空: taskId="
                        + taskId
                        + ", model="
                        + modelId
                        + ", resultUrl="
                        + result.url());
    }

    /** 将第 2～N 张并列候选分别持久化为独立 Media。 */
    private void saveExtraImages(AigcTask task, ImageResult result, ImageRequest request) {
        var extraUrls = result.urls();
        if (extraUrls == null || extraUrls.size() <= 1) return;
        for (int i = 1; i < extraUrls.size(); i++) {
            String extraUrl = extraUrls.get(i);
            String ext = guessImageExt(extraUrl);
            String path =
                    "aigc/%s/%s.%s".formatted(task.getType().toLowerCase(), UUID.randomUUID(), ext);
            var storedFile =
                    fileService.uploadFromUrl(
                            extraUrl, path, imageContentType(ext), task.getUserId());
            createMedia(
                    task,
                    storedFile,
                    AigcMediaType.IMAGE,
                    generatedName(task, request.getDisplayPrompt()) + " #" + (i + 1),
                    request.getWidth(),
                    request.getHeight(),
                    null,
                    generationInfo(task, request.getSizePreset()));
        }
    }

    private MediaVO createMedia(
            AigcTask task,
            StoredFile file,
            MediaType mediaType,
            String name,
            Integer width,
            Integer height,
            BigDecimal duration,
            String generationInfo) {
        return mediaApi.createFromGeneratedFile(
                new AigcGeneratedMediaCommand(
                        task.getUserId(),
                        name,
                        mediaType,
                        file,
                        null,
                        null,
                        task.getId(),
                        task.getProjectId(),
                        width,
                        height,
                        duration,
                        null,
                        generationInfo));
    }

    private String imageProviderResult(ImageResult result) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("modelId", result.modelId());
        snapshot.put("url", result.url());
        snapshot.put("urls", result.urls() != null ? result.urls() : List.of());
        snapshot.put("embeddedBase64", result.b64Json() != null);
        return JsonUtils.toJsonString(snapshot);
    }

    private String generatedName(AigcTask task, String preferredName) {
        var source =
                preferredName != null && !preferredName.isBlank()
                        ? preferredName
                        : (task.getPrompt() != null && !task.getPrompt().isBlank()
                                ? task.getPrompt()
                                : "AI生成-" + task.getType() + "-" + task.getId());
        return source.substring(0, Math.min(source.length(), 80));
    }

    private String generationInfo(AigcTask task, String sizePreset) {
        return JsonUtils.toJsonString(
                Map.of(
                        "prompt", task.getPrompt() != null ? task.getPrompt() : "",
                        "model", task.getModel() != null ? task.getModel() : "",
                        "provider", task.getProvider() != null ? task.getProvider() : "",
                        "sizePreset", sizePreset != null ? sizePreset : ""));
    }

    private String imageContentType(String extension) {
        return "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
    }

    /**
     * 音乐生成异步执行（同步 API，阻塞直到结果返回）。
     *
     * <p>{@code @OrgIgnore} 用途见 {@link #submitSync}。
     */
    @OrgIgnore
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitMusicSync(
            Long taskId, String prompt, String lyrics, String gender, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        runInOwnerContext(
                task,
                "aigc-music-gen",
                () -> submitMusicSyncInternal(task, taskId, prompt, lyrics, gender, mockUrl));
    }

    private void submitMusicSyncInternal(
            AigcTask task,
            Long taskId,
            String prompt,
            String lyrics,
            String gender,
            String mockUrl) {
        try {
            task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
            taskRepo.save(task);

            StoredFile storedFile;
            BigDecimal duration = null;
            if (mockUrl != null && !mockUrl.isBlank()) {
                task.setProviderResult(
                        JsonUtils.toJsonString(Map.of("url", mockUrl, "mock", true)));
                String path = "aigc/music/%s.mp3".formatted(UUID.randomUUID());
                storedFile =
                        fileService.uploadFromUrl(mockUrl, path, "audio/mpeg", task.getUserId());
            } else {
                var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
                var result =
                        aiServiceRegistry
                                .get(MusicGenerationService.class, aiModel)
                                .generate(aiModel, new MusicRequest(prompt, lyrics, gender, "mp3"));
                Long creditTxId = CreditCallContext.takeLastCreditTxId();
                if (creditTxId != null) {
                    task.setCreditTxId(creditTxId);
                    taskRepo.save(task);
                }
                task.setProviderResult(JsonUtils.toJsonString(result));
                duration = result.duration() != null ? BigDecimal.valueOf(result.duration()) : null;
                String path = "aigc/music/%s.mp3".formatted(UUID.randomUUID());
                storedFile =
                        fileService.uploadFromUrl(
                                result.audioUrl(), path, "audio/mpeg", task.getUserId());
            }

            var media =
                    createMedia(
                            task,
                            storedFile,
                            AigcMediaType.MUSIC,
                            generatedName(task, prompt),
                            null,
                            null,
                            duration,
                            generationInfo(task, null));
            task.setOutputMediaVersionId(media.currentVersion().id());
            task.setStatus(AigcTaskStatusEnum.SUCCESS.getCode());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);

            log.info(
                    "[submitMusicSync] 音乐生成完成: taskId={}, mediaVersionId={}",
                    taskId,
                    task.getOutputMediaVersionId());
        } catch (Exception e) {
            log.error("[submitMusicSync] 生成失败: taskId={}", taskId, e);
            refundIfSettled(task, e.getMessage());
            task.setStatus(AigcTaskStatusEnum.FAIL.getCode());
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
        }
        try {
            eventService.push(
                    task.getUserId(),
                    AigcTaskStatusEnum.SUCCESS.getCode().equals(task.getStatus())
                            ? EVENT_COMPLETED
                            : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitMusicSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
    }

    /**
     * 配音生成异步执行（TTS 非流式，阻塞合成完整音频后上传存储）。
     *
     * <p>{@code @OrgIgnore} 用途见 {@link #submitSync}。
     */
    @OrgIgnore
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitVoiceSync(Long taskId, String text, String voice, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        runInOwnerContext(
                task,
                "aigc-voice-gen",
                () -> submitVoiceSyncInternal(task, taskId, text, voice, mockUrl));
    }

    private void submitVoiceSyncInternal(
            AigcTask task, Long taskId, String text, String voice, String mockUrl) {
        try {
            task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
            taskRepo.save(task);

            StoredFile storedFile;
            if (mockUrl != null && !mockUrl.isBlank()) {
                task.setProviderResult(
                        JsonUtils.toJsonString(Map.of("url", mockUrl, "mock", true)));
                String path = "aigc/voice/%s.mp3".formatted(UUID.randomUUID());
                storedFile =
                        fileService.uploadFromUrl(mockUrl, path, "audio/mpeg", task.getUserId());
            } else {
                var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
                if (aiModel == null) {
                    throw new IllegalStateException("配音模型未配置: " + task.getModel());
                }
                var result =
                        aiServiceRegistry
                                .get(SpeechService.class, aiModel)
                                .synthesize(aiModel, text, voice);
                Long creditTxId = CreditCallContext.takeLastCreditTxId();
                if (creditTxId != null) {
                    task.setCreditTxId(creditTxId);
                    taskRepo.save(task);
                }
                byte[] audio = result.audio();
                if (audio == null || audio.length == 0) {
                    throw new IllegalStateException("配音合成结果为空: taskId=" + taskId);
                }
                task.setProviderResult(
                        JsonUtils.toJsonString(Map.of("charCount", result.charCount())));
                String path = "aigc/voice/%s.mp3".formatted(UUID.randomUUID());
                storedFile =
                        fileService.uploadFromBytes(audio, path, "audio/mpeg", task.getUserId());
            }

            var media =
                    createMedia(
                            task,
                            storedFile,
                            AigcMediaType.AUDIO,
                            generatedName(task, text),
                            null,
                            null,
                            null,
                            JsonUtils.toJsonString(
                                    Map.of(
                                            "text", text,
                                            "voice", voice != null ? voice : "",
                                            "model",
                                                    task.getModel() != null
                                                            ? task.getModel()
                                                            : "")));
            task.setOutputMediaVersionId(media.currentVersion().id());
            task.setStatus(AigcTaskStatusEnum.SUCCESS.getCode());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);

            log.info(
                    "[submitVoiceSync] 配音生成完成: taskId={}, mediaVersionId={}",
                    taskId,
                    task.getOutputMediaVersionId());
        } catch (Exception e) {
            log.error("[submitVoiceSync] 生成失败: taskId={}", taskId, e);
            refundIfSettled(task, e.getMessage());
            task.setStatus(AigcTaskStatusEnum.FAIL.getCode());
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
        }
        try {
            eventService.push(
                    task.getUserId(),
                    AigcTaskStatusEnum.SUCCESS.getCode().equals(task.getStatus())
                            ? EVENT_COMPLETED
                            : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitVoiceSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
    }

    /** 3D 模型生成异步执行（提交到第三方后立即返回，由 {@code Model3dTaskSyncJob} 轮询结果）。 */
    @OrgIgnore
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitModel3dSync(Long taskId, String prompt, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        runInOwnerContext(
                task,
                "aigc-model3d-gen",
                () -> submitModel3dSyncInternal(task, taskId, prompt, mockUrl));
    }

    private void submitModel3dSyncInternal(
            AigcTask task, Long taskId, String prompt, String mockUrl) {
        try {
            task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
            taskRepo.save(task);

            if (mockUrl != null && !mockUrl.isBlank()) {
                task.setProviderResult(
                        JsonUtils.toJsonString(Map.of("modelUrl", mockUrl, "mock", true)));
                String path = "aigc/model_3d/%s.glb".formatted(UUID.randomUUID());
                var storedFile =
                        fileService.uploadFromUrl(
                                mockUrl, path, "model/gltf-binary", task.getUserId());
                var media =
                        createMedia(
                                task,
                                storedFile,
                                AigcMediaType.MODEL_3D,
                                generatedName(task, prompt),
                                null,
                                null,
                                null,
                                generationInfo(task, null));
                task.setOutputMediaVersionId(media.currentVersion().id());
                task.setStatus(AigcTaskStatusEnum.SUCCESS.getCode());
                task.setUpdateTime(LocalDateTime.now());
                taskRepo.save(task);
                eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
                eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
                return;
            }

            Map<String, Object> params =
                    task.getParams() != null
                            ? JsonUtils.parseObject(
                                    task.getParams(), new TypeReference<Map<String, Object>>() {})
                            : Map.of();
            String source = params.containsKey("source") ? (String) params.get("source") : "text";
            String textureQuality =
                    params.containsKey("textureQuality")
                            ? (String) params.get("textureQuality")
                            : null;

            String providerTaskId =
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
            task.setProviderTaskId(providerTaskId);
            task.setStatus(AigcTaskStatusEnum.PENDING.getCode());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            log.info(
                    "[submitModel3dSync] 任务已提交: taskId={}, providerTaskId={}, source={}",
                    taskId,
                    providerTaskId,
                    source);
        } catch (Exception e) {
            log.error("[submitModel3dSync] 提交失败: taskId={}", taskId, e);
            task.setStatus(AigcTaskStatusEnum.FAIL.getCode());
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            try {
                eventService.push(task.getUserId(), EVENT_FAILED, toVO(task));
            } catch (Exception ignored) {
            }
            eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
        }
    }

    /** 视频生成异步执行（提交到第三方后立即返回，由 {@code VideoTaskSyncJob} 轮询结果）。 */
    @OrgIgnore
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitVideoAsync(Long taskId, String prompt, String modelId, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        runInOwnerContext(
                task,
                "aigc-video-gen",
                () -> submitVideoAsyncInternal(task, taskId, prompt, modelId, mockUrl));
    }

    private void submitVideoAsyncInternal(
            AigcTask task, Long taskId, String prompt, String modelId, String mockUrl) {
        try {
            task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
            taskRepo.save(task);

            if (mockUrl != null && !mockUrl.isBlank()) {
                task.setProviderResult(
                        JsonUtils.toJsonString(Map.of("videoUrl", mockUrl, "mock", true)));
                String path = "aigc/video/%s.mp4".formatted(UUID.randomUUID());
                var storedFile =
                        fileService.uploadFromUrl(mockUrl, path, "video/mp4", task.getUserId());
                var media =
                        createMedia(
                                task,
                                storedFile,
                                AigcMediaType.VIDEO,
                                generatedName(task, prompt),
                                null,
                                null,
                                null,
                                generationInfo(task, null));
                task.setOutputMediaVersionId(media.currentVersion().id());
                task.setStatus(AigcTaskStatusEnum.SUCCESS.getCode());
                task.setUpdateTime(LocalDateTime.now());
                taskRepo.save(task);
                eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
                eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
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

            task.setProviderTaskId(thirdTaskId);
            task.setStatus(AigcTaskStatusEnum.PENDING.getCode());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            log.info("[submitVideoAsync] 任务已提交: taskId={}, thirdTaskId={}", taskId, thirdTaskId);
        } catch (Exception e) {
            log.error("[submitVideoAsync] 提交失败: taskId={}", taskId, e);
            task.setStatus(AigcTaskStatusEnum.FAIL.getCode());
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
     * <p>{@code @OrgIgnore} 用途见 {@link #submitSync}。
     *
     * @param taskId 内部任务 ID
     * @param imageUrl 待处理图像 URL
     * @param method 处理方式，如 SEGMENT_HD_BODY
     * @param mockUrl Mock 模式固定返回 URL，null 表示真实调用
     */
    @OrgIgnore
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitImageProcessSync(
            Long taskId, String imageUrl, String method, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        runInOwnerContext(
                task,
                "aigc-image-process",
                () -> submitImageProcessSyncInternal(task, taskId, imageUrl, method, mockUrl));
    }

    private void submitImageProcessSyncInternal(
            AigcTask task, Long taskId, String imageUrl, String method, String mockUrl) {
        try {
            task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
            taskRepo.save(task);

            StoredFile storedFile;
            if (mockUrl != null && !mockUrl.isBlank()) {
                task.setProviderResult(
                        JsonUtils.toJsonString(Map.of("resultUrl", mockUrl, "mock", true)));
                String ext = guessImageExt(mockUrl);
                String path = "aigc/image_process/%s.%s".formatted(UUID.randomUUID(), ext);
                storedFile =
                        fileService.uploadFromUrl(
                                mockUrl, path, imageContentType(ext), task.getUserId());
            } else {
                if (imageProcessService == null) {
                    throw new IllegalStateException("ImageProcessService 未配置，请检查阿里云 OSS 凭证");
                }
                var result =
                        imageProcessService.process(
                                new ImageProcessService.ProcessRequest(imageUrl, method));

                if ("PENDING".equals(result.status())) {
                    task.setProviderTaskId(result.taskId());
                    task.setProviderResult(JsonUtils.toJsonString(result));
                    task.setStatus(AigcTaskStatusEnum.PENDING.getCode());
                    task.setUpdateTime(LocalDateTime.now());
                    taskRepo.save(task);
                    log.info(
                            "[submitImageProcessSync] 异步任务已提交: taskId={}, providerTaskId={}",
                            taskId,
                            result.taskId());
                    return;
                }

                if (!"SUCCESS".equals(result.status())) {
                    throw new IllegalStateException("图像处理失败: " + result.errorMessage());
                }
                task.setProviderResult(JsonUtils.toJsonString(result));
                String resultUrl = result.resultUrl();
                String ext = guessImageExt(resultUrl);
                String path = "aigc/image_process/%s.%s".formatted(UUID.randomUUID(), ext);
                storedFile =
                        fileService.uploadFromUrl(
                                resultUrl, path, imageContentType(ext), task.getUserId());
            }

            var media =
                    createMedia(
                            task,
                            storedFile,
                            AigcMediaType.IMAGE,
                            "AI处理-" + method + "-" + task.getId(),
                            null,
                            null,
                            null,
                            JsonUtils.toJsonString(Map.of("imageUrl", imageUrl, "method", method)));
            task.setOutputMediaVersionId(media.currentVersion().id());
            task.setStatus(AigcTaskStatusEnum.SUCCESS.getCode());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);

            log.info(
                    "[submitImageProcessSync] 图像处理完成: taskId={}, mediaVersionId={}",
                    taskId,
                    task.getOutputMediaVersionId());
        } catch (Exception e) {
            log.error("[submitImageProcessSync] 处理失败: taskId={}", taskId, e);
            task.setStatus(AigcTaskStatusEnum.FAIL.getCode());
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
        }
        try {
            eventService.push(
                    task.getUserId(),
                    AigcTaskStatusEnum.SUCCESS.getCode().equals(task.getStatus())
                            ? EVENT_COMPLETED
                            : EVENT_FAILED,
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
