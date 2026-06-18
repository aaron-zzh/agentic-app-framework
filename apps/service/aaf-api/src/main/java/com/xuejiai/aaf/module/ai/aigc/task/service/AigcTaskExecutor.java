package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.image.DashScopeImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService.TextTo3dRequest;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService.MusicRequest;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;

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
    private final StorageService storageService;
    private final MediaAssetService mediaAssetService;
    private final ImageServiceFactory imageServiceFactory;
    private final ObjectMapper objectMapper;
    private final AiCreditGuard creditGuard;
    private final ConfigCacheManager configCacheManager;
    private final MusicGenerationService musicGenerationService;
    private final Model3dGenerationService model3dGenerationService;
    private final ObjectProvider<SpeechService> speechServiceProvider;
    private final jakarta.persistence.EntityManager entityManager;

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
            var aiModel = configCacheManager.getAiModelByModelId(modelId);

            ImageResult result;
            if (mockUrl != null && !mockUrl.isBlank()) {
                // Mock 模式：跳过真实 API，直接构造结果
                result = new ImageResult(mockUrl, null, modelId);
            } else {
                var svc = imageServiceFactory.getSyncService(modelId);
                if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
                    if (svc instanceof DashScopeImageGenerationService ds) {
                        result = ds.generateWithImages(aiModel, p);
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

            String ossUrl;
            String firstUrl =
                    result.url() != null
                            ? result.url()
                            : (result.urls() != null && !result.urls().isEmpty()
                                    ? result.urls().get(0)
                                    : null);
            if (firstUrl != null) {
                // 判断是 b64 还是 url：b64 不含 http 且较长，或以 data: 开头
                if (firstUrl.startsWith("data:")
                        || (!firstUrl.startsWith("http") && firstUrl.length() > 200)) {
                    ossUrl = uploadB64ToOss(firstUrl, task.getType(), task.getId());
                } else {
                    ossUrl = uploadToOss(firstUrl, task.getType(), task.getId());
                }
            } else if (result.b64Json() != null) {
                ossUrl = uploadB64ToOss(result.b64Json(), task.getType(), task.getId());
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
                        String extra =
                                uploadToOss(
                                        extraUrls.get(i), task.getType(), task.getId() * 1000L + i);
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

    private String uploadToOss(String url, String type, Long taskId) {
        try {
            // 解析 URL 路径部分取扩展名，签名参数不干扰；取不到则按类型降级
            String ext = "png";
            try {
                String path = new URI(url).getPath();
                int dot = path.lastIndexOf('.');
                String e = (dot >= 0) ? path.substring(dot + 1) : "";
                if (!e.isEmpty() && e.length() <= 5) ext = e;
            } catch (Exception ignore) {
            }
            // UUID 保证唯一，不依赖 taskId 或 URL 中的任何信息
            String filename = "aigc/%s/%s.%s".formatted(type.toLowerCase(), UUID.randomUUID(), ext);
            var input = new URL(url).openStream();
            String key = storageService.upload(input, filename, "image/" + ext);
            return storageService.getUrl(key);
        } catch (Exception e) {
            log.warn("[uploadToOss] 上传失败: url={}", url, e);
            return url;
        }
    }

    private String uploadB64ToOss(String b64, String type, Long taskId) {
        try {
            // 剥离 data URL 前缀：data:image/png;base64,xxx
            String mime = "image/png";
            String ext = "png";
            String data = b64;
            if (b64 != null && b64.startsWith("data:")) {
                int comma = b64.indexOf(',');
                if (comma > 0) {
                    String header = b64.substring(5, comma); // image/png;base64
                    mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
                    ext = mime.contains("/") ? mime.substring(mime.indexOf('/') + 1) : "png";
                    if (ext.equals("jpeg")) ext = "jpg";
                    data = b64.substring(comma + 1);
                }
            }
            byte[] bytes = Base64.getDecoder().decode(data);
            String filename = "aigc/%s/%d.%s".formatted(type.toLowerCase(), taskId, ext);
            String key =
                    storageService.upload(new java.io.ByteArrayInputStream(bytes), filename, mime);
            return storageService.getUrl(key);
        } catch (Exception e) {
            log.warn("[uploadB64ToOss] 上传失败: taskId={}", taskId, e);
            throw new IllegalStateException("图片上传 OSS 失败: taskId=" + taskId, e);
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
                                    java.util.Map.of(
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
                                    java.util.Map.of(
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
                var result =
                        musicGenerationService.generate(
                                null, new MusicRequest(prompt, lyrics, gender, "mp3"));
                task.setResultUrl(result.audioUrl());
                ossUrl = uploadAudioToOss(result.audioUrl(), task.getId());
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

    /** 配音生成异步执行（TTS 非流式，阻塞合成完整音频后上传 OSS）。 */
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
                var speechService = speechServiceProvider.getIfAvailable();
                if (speechService == null) {
                    throw new IllegalStateException("配音服务未启用，请配置 spring.ai.dashscope.api-key");
                }

                byte[] audio = speechService.synthesize(text, voice);
                if (audio == null || audio.length == 0) {
                    throw new IllegalStateException("配音合成结果为空: taskId=" + taskId);
                }
                ossUrl = uploadAudioBytesToOss(audio, task.getId());
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
                                        java.util.Map.of(
                                                "text", text, "voice", voice != null ? voice : "")),
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

    /** 将 TTS 合成的音频字节（MP3）上传 OSS，返回可访问 URL。 */
    private String uploadAudioBytesToOss(byte[] audio, Long taskId) {
        try {
            String filename = "aigc/voice/%s.mp3".formatted(UUID.randomUUID());
            String key =
                    storageService.upload(
                            new java.io.ByteArrayInputStream(audio), filename, "audio/mpeg");
            return storageService.getUrl(key);
        } catch (Exception e) {
            log.warn("[uploadAudioBytesToOss] 上传失败: taskId={}", taskId, e);
            throw new IllegalStateException("配音上传 OSS 失败: taskId=" + taskId, e);
        }
    }

    /** 3D 模型生成异步执行（提交后轮询直到完成，最多等待 10 分钟）。 */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitModel3dSync(Long taskId, String prompt, String mockUrl) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        try {
            task.setStatus("RUNNING");
            taskRepo.save(task);

            String ossUrl;
            if (mockUrl != null && !mockUrl.isBlank()) {
                ossUrl = mockUrl;
            } else {
                // 提交到第三方，拿到外部 taskId
                String thirdTaskId =
                        model3dGenerationService.submitTextTo3d(
                                new TextTo3dRequest(prompt, null, null));
                task.setTaskId(thirdTaskId);
                taskRepo.save(task);
                log.info(
                        "[submitModel3dSync] 任务已提交: taskId={}, thirdTaskId={}",
                        taskId,
                        thirdTaskId);

                // 轮询结果，每 10 秒一次，最多 180 次 = 30 分钟
                com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService
                                .Model3dTaskResult
                        result = null;
                for (int i = 0; i < 180; i++) {
                    Thread.sleep(10000);
                    result = model3dGenerationService.query(thirdTaskId);
                    if ((i + 1) % 6 == 0) {
                        log.info(
                                "[submitModel3dSync] 轮询中: taskId={}, status={}, elapsed={}min",
                                taskId,
                                result.status(),
                                (i + 1) / 6);
                    }
                    if (result.status()
                                    == com.xuejiai.aaf.framework.intelligent.ai.model3d
                                            .Model3dGenerationService.Model3dTaskResult.TaskStatus
                                            .SUCCEEDED
                            || result.status()
                                    == com.xuejiai.aaf.framework.intelligent.ai.model3d
                                            .Model3dGenerationService.Model3dTaskResult.TaskStatus
                                            .FAILED) {
                        break;
                    }
                }

                if (result == null
                        || result.status()
                                != com.xuejiai.aaf.framework.intelligent.ai.model3d
                                        .Model3dGenerationService.Model3dTaskResult.TaskStatus
                                        .SUCCEEDED) {
                    throw new RuntimeException(
                            "3D 生成失败或超时: status=" + (result != null ? result.status() : "null"));
                }

                String sourceUrl =
                        result.modelUrl() != null ? result.modelUrl() : result.baseModelUrl();
                ossUrl = uploadModel3dToOss(sourceUrl, task.getId());
                task.setResultUrl(sourceUrl);
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
                                        : "AI 3D模型",
                                MediaAssetType.MODEL_3D,
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
                log.warn("[submitModel3dSync] 写入素材库失败: taskId={}", taskId, e);
            }

            log.info("[submitModel3dSync] 3D 生成完成: taskId={}, ossUrl={}", taskId, ossUrl);
        } catch (Exception e) {
            log.error("[submitModel3dSync] 生成失败: taskId={}", taskId, e);
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
            log.debug("[submitModel3dSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
    }

    private String uploadModel3dToOss(String url, Long taskId) {
        try {
            String filename = "aigc/model3d/%s.glb".formatted(UUID.randomUUID());
            var input = new URL(url).openStream();
            String key = storageService.upload(input, filename, "model/gltf-binary");
            return storageService.getUrl(key);
        } catch (Exception e) {
            log.warn("[uploadModel3dToOss] 上传失败，回退原始 URL: taskId={}", taskId, e);
            return url;
        }
    }

    private String uploadAudioToOss(String url, Long taskId) {
        try {
            String filename = "aigc/music/%s.mp3".formatted(UUID.randomUUID());
            var input = new URL(url).openStream();
            String key = storageService.upload(input, filename, "audio/mpeg");
            return storageService.getUrl(key);
        } catch (Exception e) {
            log.warn("[uploadAudioToOss] 上传失败，回退原始 URL: taskId={}", taskId, e);
            return url;
        }
    }

    /** 从 task.params JSON 反序列化为 ImageRequest，再补充 prompt/modelId。 */
    private ImageRequest parseImageParams(String prompt, String modelId, String paramsJson) {
        try {
            ImageRequest req =
                    paramsJson != null
                            ? objectMapper.readValue(paramsJson, ImageRequest.class)
                            : new ImageRequest();
            req.setPrompt(prompt);
            req.setModelId(modelId);
            return req;
        } catch (Exception ignore) {
            return new ImageRequest(prompt, modelId);
        }
    }

    private String resolveBizName(String taskType) {
        if (taskType == null) return null;
        return switch (taskType) {
            case "IMAGE" -> "图像生成";
            case "VIDEO" -> "视频生成";
            case "MODEL_3D" -> "3D 生成";
            case "MUSIC" -> "音乐生成";
            case "VOICE" -> "语音合成";
            default -> null;
        };
    }

    private AigcTaskVO toVO(AigcTask task) {
        return taskMapper.toVO(task);
    }
}
