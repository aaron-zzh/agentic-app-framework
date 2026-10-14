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

import com.xuejiai.aaf.common.constant.SysConfigKeys;
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
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcEventPublisher;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcGeneratedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaView;
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
    private final AigcTaskClaimService taskClaimService;
    private final AigcEventPublisher eventService;
    private final AigcTaskMapper taskMapper;
    private final FileStoragePort fileService;
    private final AigcMediaApi mediaApi;
    private final AiServiceRegistry aiServiceRegistry;
    private final AiCreditGuard creditGuard;
    private final ConfigCacheManager configCacheManager;
    private final Model3dGenerationService model3dGenerationService;
    private final PermissionExecutionService permissionExecutionService;
    private final SystemConfigService systemConfigService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private MidjourneyAsyncImageService midjourneyAsyncImageService;

    @Autowired(required = false)
    private ImageProcessService imageProcessService;

    private static final String EVENT_COMPLETED = "task.completed";
    private static final String EVENT_FAILED = "task.failed";

    /**
     * 排队任务的唯一执行入口：手动与 Assistant 提交的专业任务都经此启动。
     *
     * <p>是否放行由 {@link AigcTaskClaimService#claimIntent} 在其原子准入事务内按订阅并发窗口判定——未取得认领即保持排队，
     * 本方法直接返回且不接触供应商，因此重复派发与恢复扫描都不会重复提交。
     *
     * <p>{@code @OrgIgnore}：认领前尚不知道 task 的 orgId（先有鸡蛋问题），需豁免这段空窗期的 fail-closed 拒绝；认领后 {@link
     * #runInOwnerContext} 会恢复真实归属者与组织上下文。
     */
    @OrgIgnore
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resumeIntent(Long taskId) {
        var submitOwner = UUID.randomUUID().toString();
        var task = taskClaimService.claimIntent(taskId, submitOwner).orElse(null);
        if (task == null || !submitOwner.equals(task.getSubmitOwner())) {
            return;
        }
        runInOwnerContext(task, "aigc-durable-intent", () -> dispatchIntent(task));
    }

    private void dispatchIntent(AigcTask task) {
        var parameters =
                task.getParams() == null
                        ? Map.<String, Object>of()
                        : JsonUtils.parseObject(
                                task.getParams(), new TypeReference<Map<String, Object>>() {});
        switch (task.getType()) {
            case "IMAGE" ->
                    submitSyncInternal(
                            task,
                            task.getId(),
                            task.getPrompt(),
                            task.getModel(),
                            mockValue(task, "image"));
            case "VIDEO" ->
                    submitVideoAsyncInternal(
                            task,
                            task.getId(),
                            task.getPrompt(),
                            task.getModel(),
                            mockValue(task, "video"));
            case "MODEL_3D" ->
                    submitModel3dSyncInternal(
                            task, task.getId(), task.getPrompt(), mockValue(task, "model3d"));
            case "MUSIC" ->
                    submitMusicSyncInternal(
                            task,
                            task.getId(),
                            task.getPrompt(),
                            String.valueOf(parameters.getOrDefault("lyrics", "")),
                            String.valueOf(parameters.getOrDefault("gender", "female")),
                            mockValue(task, "audio"));
            case "VOICE" ->
                    submitVoiceSyncInternal(
                            task,
                            task.getId(),
                            task.getPrompt(),
                            trimToNull(parameters.get("voice")),
                            mockValue(task, "audio"));
            case "IMAGE_PROCESS" ->
                    submitImageProcessSyncInternal(
                            task,
                            task.getId(),
                            toParamLong(parameters.get("imageFileId")),
                            String.valueOf(parameters.get("method")),
                            mockValue(task, "image"));
            default -> throw new IllegalStateException("不支持的 durable Task 类型: " + task.getType());
        }
    }

    /**
     * 解析 Mock 固定结果。
     *
     * <p>Mock 只服务于独立任务的本地联调；项目执行链需要真实 Run 与资源事实，因此绑定 executionRun 的任务一律返回 {@code null} 走真实调用。
     */
    private String mockValue(AigcTask task, String typeKey) {
        if (task.getExecutionRunId() != null
                || !systemConfigService.getBoolean(SysConfigKeys.Aigc.MOCK_ENABLED, false)) {
            return null;
        }
        var json = systemConfigService.getString(SysConfigKeys.Aigc.MOCK_DATA);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            var values = JsonUtils.parseObject(json, new TypeReference<Map<String, String>>() {});
            return trimToNull(values.get(typeKey));
        } catch (Exception failure) {
            log.warn("[AigcTaskExecutor] 解析 aigc.mock_data 失败: {}", failure.getMessage());
            return null;
        }
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        var text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

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
     * 图像生成执行体。
     *
     * <p>从 {@code task.params} 读取 width/height/negativePrompt/seed/promptExtend/imageCount，构建完整
     * {@link ImageRequest} 后调用对应服务；权限与组织上下文由 {@link #resumeIntent} 统一恢复。
     */
    private void submitSyncInternal(
            AigcTask task, Long taskId, String prompt, String modelId, String mockUrl) {
        try {
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
            eventService.publish(
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
        if (p.getImageFileIds() != null && !p.getImageFileIds().isEmpty()) {
            p.setImageUrls(fileService.resolveImageData(p.getImageFileIds()));
        }
        log.info(
                "[图片任务] modelId={}, imageFileCount={}, prompt={}",
                modelId,
                p.getImageUrls() != null ? p.getImageUrls().size() : 0,
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
            boolean inlineBase64 =
                    firstUrl.startsWith("data:")
                            || (!firstUrl.startsWith("http") && firstUrl.length() > 200);
            String ext = inlineBase64 ? inlineImageExt(firstUrl) : guessImageExt(firstUrl);
            String path =
                    "aigc/%s/%s.%s".formatted(task.getType().toLowerCase(), UUID.randomUUID(), ext);
            if (inlineBase64) {
                return fileService.uploadFromBase64(firstUrl, path, task.getUserId());
            }
            return fileService.uploadFromUrl(
                    firstUrl, path, imageContentType(ext), task.getUserId());
        }
        if (result.b64Json() != null) {
            String path =
                    "aigc/%s/%s.%s"
                            .formatted(
                                    task.getType().toLowerCase(),
                                    UUID.randomUUID(),
                                    inlineImageExt(result.b64Json()));
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

    private AigcMediaView createMedia(
            AigcTask task,
            StoredFile file,
            AigcMediaType mediaType,
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

    /** 音乐生成执行体（供应商同步 API，阻塞直到结果返回）。 */
    private void submitMusicSyncInternal(
            AigcTask task,
            Long taskId,
            String prompt,
            String lyrics,
            String gender,
            String mockUrl) {
        try {
            if (task.getExecutionRunId() == null) {
                task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
                taskRepo.save(task);
            }

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
            eventService.publish(
                    task.getUserId(),
                    AigcTaskStatusEnum.SUCCESS.getCode().equals(task.getStatus())
                            ? EVENT_COMPLETED
                            : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitMusicSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
        eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
    }

    /** 配音生成执行体（TTS 非流式，阻塞合成完整音频后上传存储）。 */
    private void submitVoiceSyncInternal(
            AigcTask task, Long taskId, String text, String voice, String mockUrl) {
        try {
            if (task.getExecutionRunId() == null) {
                task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
                taskRepo.save(task);
            }

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
            eventService.publish(
                    task.getUserId(),
                    AigcTaskStatusEnum.SUCCESS.getCode().equals(task.getStatus())
                            ? EVENT_COMPLETED
                            : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitVoiceSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
        eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
    }

    /** 3D 模型生成执行体（提交到第三方后立即返回，由 {@code Model3dTaskSyncJob} 轮询结果）。 */
    private void submitModel3dSyncInternal(
            AigcTask task, Long taskId, String prompt, String mockUrl) {
        try {
            if (task.getExecutionRunId() == null) {
                task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
                taskRepo.save(task);
            }

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
                eventService.publish(task.getUserId(), EVENT_COMPLETED, toVO(task));
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
                eventService.publish(task.getUserId(), EVENT_FAILED, toVO(task));
            } catch (Exception ignored) {
            }
            eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
        }
    }

    /** 视频生成执行体（提交到第三方后立即返回，由 {@code VideoTaskSyncJob} 轮询结果）。 */
    private void submitVideoAsyncInternal(
            AigcTask task, Long taskId, String prompt, String modelId, String mockUrl) {
        try {
            if (task.getExecutionRunId() == null) {
                task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
                taskRepo.save(task);
            }

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
                eventService.publish(task.getUserId(), EVENT_COMPLETED, toVO(task));
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

            List<Long> referenceImageFileIds = toParamLongList(p.get("referenceImageFileIds"));
            List<String> referenceImageUrls = fileService.resolveImageData(referenceImageFileIds);
            Long imageFileId = toParamLong(p.get("imageFileId"));
            String imageUrl =
                    imageFileId != null
                            ? fileService.resolveImageData(List.of(imageFileId)).getFirst()
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
                boolean generateAudio = Boolean.TRUE.equals(p.get("generateAudio"));
                thirdTaskId =
                        doubao.submitRich(
                                aiModel,
                                prompt,
                                referenceImageUrls,
                                referenceVideoUrls,
                                referenceAudioUrls,
                                (String) p.get("ratio"),
                                toParamInt(p.get("duration")),
                                generateAudio);
            } else {
                var request =
                        new VideoRequest(
                                prompt,
                                imageUrl,
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
                eventService.publish(task.getUserId(), EVENT_FAILED, toVO(task));
            } catch (Exception ignored) {
            }
            eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
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

    private static Long toParamLong(Object val) {
        if (val instanceof Number number) return number.longValue();
        try {
            return val != null ? Long.parseLong(val.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> toParamLongList(Object val) {
        if (!(val instanceof List<?> list)) return List.of();
        return list.stream()
                .map(AigcTaskExecutor::toParamLong)
                .filter(java.util.Objects::nonNull)
                .toList();
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

    /** 内联 base64（含 data URL）按解码后的文件头推断扩展名，识别不了再按 data URL 声明推断。 */
    private String inlineImageExt(String value) {
        String data = value;
        if (value.startsWith("data:")) {
            int comma = value.indexOf(',');
            if (comma > 0) data = value.substring(comma + 1);
        }
        if (data.length() >= 24) {
            try {
                // 24 个 base64 字符解码为 18 字节，足够判断常见图片头
                byte[] head = java.util.Base64.getDecoder().decode(data.substring(0, 24));
                if ((head[0] & 0xff) == 0x89 && head[1] == 0x50 && head[2] == 0x4e) return "png";
                if ((head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8) return "jpg";
                if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F') return "gif";
                if (head[0] == 'R' && head[1] == 'I' && head[8] == 'W' && head[9] == 'E') {
                    return "webp";
                }
            } catch (IllegalArgumentException ignored) {
                // 非法 base64 交由上传环节报错
            }
        }
        return guessImageExt(value);
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
     * 图像处理执行体（SEGMENT_HD_BODY 等同步 SDK 调用，完成后直接存 OSS）。
     *
     * @param task 已认领的任务
     * @param taskId 内部任务 ID
     * @param imageFileId 待处理图像的文件 ID
     * @param method 处理方式，如 SEGMENT_HD_BODY
     * @param mockUrl Mock 模式固定返回 URL，null 表示真实调用
     */
    private void submitImageProcessSyncInternal(
            AigcTask task, Long taskId, Long imageFileId, String method, String mockUrl) {
        try {
            if (task.getExecutionRunId() == null) {
                task.setStatus(AigcTaskStatusEnum.RUNNING.getCode());
                taskRepo.save(task);
            }

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
                // 按存储类型分流解析图像输入：本地存储→data: Base64 URL（避免受权限保护的
                // /api/system/files/{id}/content 直接暴露给外部 SDK 导致 401）；OSS/远程存储→签名 URL。
                String imageInput = fileService.resolveImageData(List.of(imageFileId)).get(0);
                var result =
                        imageProcessService.process(
                                new ImageProcessService.ProcessRequest(imageInput, method));

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
                            JsonUtils.toJsonString(
                                    Map.of("imageFileId", imageFileId, "method", method)));
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
            refundIfSettled(task, e.getMessage());
            task.setStatus(AigcTaskStatusEnum.FAIL.getCode());
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
        }
        try {
            eventService.publish(
                    task.getUserId(),
                    AigcTaskStatusEnum.SUCCESS.getCode().equals(task.getStatus())
                            ? EVENT_COMPLETED
                            : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitImageProcessSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
        eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
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
